package com.hotel.backend.controller;

import com.hotel.backend.model.Reservation;
import com.hotel.backend.model.Room;
import com.hotel.backend.model.BookingWindow;
import com.hotel.backend.repository.BookingWindowRepository;
import com.hotel.backend.repository.ReservationRepository;
import com.hotel.backend.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "*")
public class ReservationController {
    private static final DateTimeFormatter BOOKING_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM uuuu", Locale.ENGLISH);

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingWindowRepository bookingWindowRepository;

    @GetMapping
    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reservation> getReservationById(@PathVariable Long id) {
        return reservationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public List<Reservation> getReservationsByUserId(@PathVariable String userId) {
        return reservationRepository.findByUserId(userId);
    }

    @PostMapping("/recover-guest-bookings")
    @Transactional
    public ResponseEntity<?> recoverGuestBookings(@RequestBody GuestBookingRecovery recovery) {
        if (recovery.userId() == null || !recovery.userId().startsWith("guest-")) {
            return ResponseEntity.badRequest().body("A guest account is required to recover anonymous bookings.");
        }
        if (isBlank(recovery.email()) || isBlank(recovery.phone())) {
            return ResponseEntity.badRequest().body("Enter the email address and phone number used for your booking.");
        }
        String phoneDigits = recovery.phone().replaceAll("\\D", "");
        if (phoneDigits.length() < 7 || phoneDigits.length() > 15) {
            return ResponseEntity.badRequest().body("Enter the valid phone number used for your booking.");
        }

        List<Reservation> matchingBookings = reservationRepository
                .findByGuestEmailIgnoreCase(recovery.email().trim())
                .stream()
                .filter(reservation -> reservation.getUserId() != null
                        && reservation.getUserId().startsWith("guest-")
                        && !recovery.userId().equals(reservation.getUserId())
                        && reservation.getGuestPhone() != null
                        && phoneDigits.equals(reservation.getGuestPhone().replaceAll("\\D", "")))
                .toList();
        if (matchingBookings.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        matchingBookings.forEach(reservation -> reservation.setUserId(recovery.userId()));
        return ResponseEntity.ok(reservationRepository.saveAll(matchingBookings));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createReservation(@RequestBody Reservation reservation) {
        String dateError = validateBookingDates(reservation.getCheckInDate(), reservation.getCheckOutDate());
        if (dateError != null) {
            return ResponseEntity.badRequest().body(dateError);
        }
        String guestDetailsError = validateGuestDetails(reservation);
        if (guestDetailsError != null) {
            return ResponseEntity.badRequest().body(guestDetailsError);
        }
        if (reservation.getStatus() == null) {
            reservation.setStatus("Confirmed");
        }
        if (reservation.getPaymentStatus() == null) {
            reservation.setPaymentStatus("Paid");
        }
        if (reservation.getGuestCount() == null) {
            reservation.setGuestCount(2);
        }
        reservation.setDates(formatBookingDates(reservation.getCheckInDate(), reservation.getCheckOutDate()));

        Optional<Room> roomOption = findRoomForUpdate(reservation.getRoomId());
        if (roomOption.isEmpty()) {
            return ResponseEntity.badRequest().body("A valid room is required for a booking.");
        }
        Room room = roomOption.get();
        if (reservation.getGuestCount() < 1 || reservation.getGuestCount() > room.getCapacity()) {
            return ResponseEntity.badRequest().body("Guest count exceeds this room's capacity.");
        }
        if ("Maintenance".equals(room.getStatus())) {
            return ResponseEntity.status(409).body("This room is currently under maintenance.");
        }
        Optional<Reservation> conflictingReservation = reservationRepository
                .findOverlappingReservations(room.getId().toString(), reservation.getCheckInDate(),
                        reservation.getCheckOutDate())
                .stream()
                .findFirst();
        if (conflictingReservation.isPresent()) {
            Reservation conflict = conflictingReservation.get();
            return ResponseEntity.status(409).body("This room has an active booking from "
                    + conflict.getCheckInDate() + " through " + conflict.getCheckOutDate() + ".");
        }

        Reservation savedReservation = reservationRepository.save(reservation);
        synchronizeRoomStatus(room.getId().toString());
        return ResponseEntity.ok(savedReservation);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Reservation> updateReservation(@PathVariable Long id, @RequestBody Reservation details) {
        return reservationRepository.findById(id).map(res -> {
            String roomId = res.getRoomId();
            boolean datesChanged = (details.getCheckInDate() != null || details.getCheckOutDate() != null)
                    && (!Objects.equals(details.getCheckInDate(), res.getCheckInDate())
                    || !Objects.equals(details.getCheckOutDate(), res.getCheckOutDate()));
            if (datesChanged) {
                LocalDate checkInDate = details.getCheckInDate() == null ? res.getCheckInDate() : details.getCheckInDate();
                LocalDate checkOutDate = details.getCheckOutDate() == null ? res.getCheckOutDate() : details.getCheckOutDate();
                String dateError = validateBookingDates(checkInDate, checkOutDate);
                if (dateError != null) {
                    return ResponseEntity.badRequest().<Reservation>build();
                }
                if (hasDateConflict(roomId, checkInDate, checkOutDate, id)) {
                    return ResponseEntity.status(409).<Reservation>build();
                }
                res.setCheckInDate(checkInDate);
                res.setCheckOutDate(checkOutDate);
                res.setDates(formatBookingDates(checkInDate, checkOutDate));
            } else if (details.getDates() != null) {
                res.setDates(details.getDates());
            }
            res.setStatus(details.getStatus());
            res.setPaymentStatus(details.getPaymentStatus());
            res.setAmount(details.getAmount());
            if (details.getGuestCount() != null) {
                res.setGuestCount(details.getGuestCount());
            }
            if (details.getGuestName() != null) res.setGuestName(details.getGuestName());
            if (details.getGuestEmail() != null) res.setGuestEmail(details.getGuestEmail());
            if (details.getGuestPhone() != null) res.setGuestPhone(details.getGuestPhone());
            Reservation savedReservation = reservationRepository.save(res);
            synchronizeRoomStatus(roomId);
            return ResponseEntity.ok(savedReservation);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        return reservationRepository.findById(id).map(reservation -> {
            String roomId = reservation.getRoomId();
            reservationRepository.deleteById(id);
            synchronizeRoomStatus(roomId);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private Optional<Room> findRoom(String roomId) {
        try {
            return roomRepository.findById(Long.parseLong(roomId));
        } catch (NumberFormatException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private Optional<Room> findRoomForUpdate(String roomId) {
        try {
            return roomRepository.findByIdForUpdate(Long.parseLong(roomId));
        } catch (NumberFormatException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private String validateBookingDates(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null || checkOutDate == null) {
            return "Check-in and check-out dates are required.";
        }
        if (checkInDate.isBefore(LocalDate.now())) {
            return "Check-in date cannot be in the past.";
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            return "Check-out must be after check-in.";
        }
        BookingWindow window = bookingWindowRepository.findById(1L).orElseGet(() -> {
            LocalDate startDate = LocalDate.now();
            return bookingWindowRepository.save(BookingWindow.builder()
                    .id(1L)
                    .bookingStartDate(startDate)
                    .bookingEndDate(startDate.plusMonths(6).minusDays(1))
                    .build());
        });
        if (checkInDate.isBefore(window.getBookingStartDate()) || checkOutDate.isAfter(window.getBookingEndDate())) {
            return "Bookings are currently open only through " + window.getBookingEndDate() + ".";
        }
        return null;
    }

    private String validateGuestDetails(Reservation reservation) {
        if (isBlank(reservation.getGuestName()) || isBlank(reservation.getGuestEmail())
                || isBlank(reservation.getGuestPhone())) {
            return "Name, email, and phone are required.";
        }
        if (!reservation.getGuestEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "Enter a valid email address.";
        }
        long phoneDigits = reservation.getGuestPhone().chars().filter(Character::isDigit).count();
        if (phoneDigits < 7 || phoneDigits > 15) {
            return "Enter a valid phone number.";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record GuestBookingRecovery(String userId, String email, String phone) {}

    private boolean hasDateConflict(String roomId, LocalDate checkInDate, LocalDate checkOutDate,
            Long excludedReservationId) {
        return reservationRepository.findOverlappingReservations(roomId, checkInDate, checkOutDate).stream()
                .anyMatch(reservation -> !Objects.equals(reservation.getId(), excludedReservationId));
    }

    private String formatBookingDates(LocalDate checkInDate, LocalDate checkOutDate) {
        return checkInDate.format(BOOKING_DATE_FORMAT) + " - " + checkOutDate.format(BOOKING_DATE_FORMAT);
    }

    private void synchronizeRoomStatus(String roomId) {
        findRoom(roomId).ifPresent(room -> {
            LocalDate today = LocalDate.now();
            boolean hasActiveReservation = reservationRepository.findAll().stream()
                    .anyMatch(reservation -> roomId.equals(reservation.getRoomId())
                            && !isReleasedReservation(reservation.getStatus())
                            && reservation.getCheckInDate() != null
                            && reservation.getCheckOutDate() != null
                            && !today.isBefore(reservation.getCheckInDate())
                            && today.isBefore(reservation.getCheckOutDate()));
            if (hasActiveReservation) {
                if (!"Maintenance".equals(room.getStatus())) {
                    room.setStatus("Occupied");
                }
            } else if ("Occupied".equals(room.getStatus())) {
                room.setStatus("Available");
            }
            roomRepository.save(room);
        });
    }

    private boolean isReleasedReservation(String status) {
        return status != null && (status.trim().equalsIgnoreCase("Cancelled")
                || status.trim().equalsIgnoreCase("Checked Out"));
    }
}
