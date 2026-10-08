package com.hotel.backend.controller;

import com.hotel.backend.model.Reservation;
import com.hotel.backend.model.Room;
import com.hotel.backend.model.RoomSwapRequest;
import com.hotel.backend.model.User;
import com.hotel.backend.repository.ReservationRepository;
import com.hotel.backend.repository.RoomRepository;
import com.hotel.backend.repository.RoomSwapRequestRepository;
import com.hotel.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/room-swaps")
@CrossOrigin(origins = "*")
public class RoomSwapController {
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "PAYMENT_REQUIRED", "ACCEPTED", "ADMIN_APPROVED");

    private final RoomSwapRequestRepository swapRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public RoomSwapController(RoomSwapRequestRepository swapRepository,
                              ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              UserRepository userRepository) {
        this.swapRepository = swapRepository;
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getRoomSwaps(@RequestParam String userId) {
        Optional<User> user = findUser(userId);
        if (user.isPresent() && isStaffRole(user.get().getRole())) {
            return ResponseEntity.ok(swapRepository.findAll());
        }
        if (isGuest(userId)) {
            return ResponseEntity.ok(swapRepository.findByRequesterUserIdOrTargetUserId(userId, userId));
        }
        if (user.isEmpty()) return ResponseEntity.status(403).body("A signed-in account is required.");
        return ResponseEntity.status(403).body("This account cannot access room swap requests.");
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createRequest(@RequestBody CreateSwapRequest request) {
        if (request.requesterBookingId() == null || request.targetBookingId() == null
                || Objects.equals(request.requesterBookingId(), request.targetBookingId())) {
            return ResponseEntity.badRequest().body("Choose two different bookings.");
        }
        if (!isGuest(request.requesterGuestId())) {
            return ResponseEntity.status(403).body("A signed-in guest is required to request a room swap.");
        }

        List<Reservation> bookings = lockBookings(request.requesterBookingId(), request.targetBookingId());
        if (bookings.size() != 2) return ResponseEntity.badRequest().body("Both bookings must exist.");
        Reservation requester = bookings.get(0);
        Reservation target = bookings.get(1);
        if (!Objects.equals(requester.getUserId(), request.requesterGuestId())) {
            return ResponseEntity.status(403).body("You can only request a swap for your own booking.");
        }
        if (!areEligibleForSwap(requester, target)) {
            return ResponseEntity.badRequest().body("Room swaps require two active confirmed bookings with exactly matching stay dates.");
        }
        if (Objects.equals(requester.getRoomId(), target.getRoomId())) {
            return ResponseEntity.badRequest().body("The bookings must be assigned to different rooms.");
        }
        if (hasActiveRequest(requester.getId(), target.getId())) {
            return ResponseEntity.status(409).body("One of these bookings already has an active room swap request.");
        }

        Optional<Room> requesterRoomOption = findRoom(requester.getRoomId());
        Optional<Room> targetRoomOption = findRoom(target.getRoomId());
        if (requesterRoomOption.isEmpty() || targetRoomOption.isEmpty()) {
            return ResponseEntity.badRequest().body("A room assigned to one of these bookings no longer exists.");
        }
        if ("Maintenance".equalsIgnoreCase(requesterRoomOption.get().getStatus())
                || "Maintenance".equalsIgnoreCase(targetRoomOption.get().getStatus())) {
            return ResponseEntity.badRequest().body("A room under maintenance cannot be swapped.");
        }
        if (!matchesPreference(requesterRoomOption.get(), targetRoomOption.get(), request.preferences())) {
            return ResponseEntity.badRequest().body("The selected room does not match your preferences.");
        }

        RoomSwapRequest saved = swapRepository.save(RoomSwapRequest.builder()
                .requesterReservationId(requester.getId())
                .targetReservationId(target.getId())
                .requesterUserId(requester.getUserId())
                .targetUserId(target.getUserId())
                .requesterRoomId(requester.getRoomId())
                .targetRoomId(target.getRoomId())
                .preferredBalcony(request.preferences().balcony())
                .preferredFloor(request.preferences().floor())
                .preferredRoomType(request.preferences().roomType())
                .preferredBedType(request.preferences().bedType())
                .preferredView(request.preferences().view())
                .additionalAmount(calculateAdditionalAmount(requester, target, requesterRoomOption.get(), targetRoomOption.get()))
                .additionalAmountPaid(false)
                .targetAccepted(false)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/accept")
    @Transactional
    public ResponseEntity<?> acceptRequest(@PathVariable Long id, @RequestBody StaffAction action) {
        Optional<RoomSwapRequest> swapOption = swapRepository.findByIdForUpdate(id);
        if (swapOption.isEmpty()) return ResponseEntity.notFound().build();
        RoomSwapRequest swap = swapOption.get();
        if (!"PENDING".equals(swap.getStatus())) {
            return ResponseEntity.badRequest().body("This room swap is no longer waiting for receptionist review.");
        }
        if (!isStaff(action.userId())) {
            return ResponseEntity.status(403).body("Only hotel staff can accept a room swap request.");
        }
        List<Reservation> bookings = lockBookings(swap.getRequesterReservationId(), swap.getTargetReservationId());
        if (!areEligibleForSwap(swap, bookings)) {
            return ResponseEntity.badRequest().body("These bookings are no longer eligible for a room swap.");
        }
        Reservation requester = bookings.get(0);
        Reservation target = bookings.get(1);
        Optional<Room> requesterRoom = findRoom(requester.getRoomId());
        Optional<Room> targetRoom = findRoom(target.getRoomId());
        if (requesterRoom.isEmpty() || targetRoom.isEmpty()) {
            return ResponseEntity.badRequest().body("A room assigned to one of these bookings no longer exists.");
        }
        swap.setAdditionalAmount(calculateAdditionalAmount(requester, target, requesterRoom.get(), targetRoom.get()));
        swap.setTargetAccepted(true);
        swap.setStatus(swap.getAdditionalAmount() > 0 ? "PAYMENT_REQUIRED" : "ACCEPTED");
        return ResponseEntity.ok(swapRepository.save(swap));
    }

    @PostMapping("/{id}/pay-difference")
    @Transactional
    public ResponseEntity<?> payPriceDifference(@PathVariable Long id, @RequestBody PayDifferenceAction action) {
        Optional<RoomSwapRequest> swapOption = swapRepository.findByIdForUpdate(id);
        if (swapOption.isEmpty()) return ResponseEntity.notFound().build();
        RoomSwapRequest swap = swapOption.get();
        if (!"PAYMENT_REQUIRED".equals(swap.getStatus())) {
            return ResponseEntity.badRequest().body("This room swap does not have an outstanding price difference.");
        }
        if (!isGuest(action.userId()) || !Objects.equals(swap.getRequesterUserId(), action.userId())) {
            return ResponseEntity.status(403).body("Only the guest requesting the higher-priced room can pay the difference.");
        }
        List<Reservation> bookings = lockBookings(swap.getRequesterReservationId(), swap.getTargetReservationId());
        if (!areEligibleForSwap(swap, bookings)) {
            return ResponseEntity.badRequest().body("These bookings are no longer eligible for a room swap.");
        }
        Reservation requester = bookings.get(0);
        Reservation target = bookings.get(1);
        Optional<Room> requesterRoom = findRoom(requester.getRoomId());
        Optional<Room> targetRoom = findRoom(target.getRoomId());
        if (requesterRoom.isEmpty() || targetRoom.isEmpty()) {
            return ResponseEntity.badRequest().body("A room assigned to one of these bookings no longer exists.");
        }
        swap.setAdditionalAmount(calculateAdditionalAmount(requester, target, requesterRoom.get(), targetRoom.get()));
        if (swap.getAdditionalAmount() <= 0) {
            swap.setAdditionalAmountPaid(true);
        } else {
            double submittedAmount = action.amount() == null ? -1 : Math.round(action.amount() * 100.0) / 100.0;
            if (Double.compare(submittedAmount, swap.getAdditionalAmount()) != 0) {
                return ResponseEntity.status(409).body("The room-rate difference changed. Refresh the request and confirm the updated amount.");
            }
            swap.setAdditionalAmountPaid(true);
        }
        swap.setStatus("ACCEPTED");
        return ResponseEntity.ok(swapRepository.save(swap));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<?> rejectRequest(@PathVariable Long id, @RequestBody StaffAction action) {
        Optional<RoomSwapRequest> swapOption = swapRepository.findByIdForUpdate(id);
        if (swapOption.isEmpty()) return ResponseEntity.notFound().build();
        RoomSwapRequest swap = swapOption.get();
        if (!"PENDING".equals(swap.getStatus())) {
            return ResponseEntity.badRequest().body("This room swap is no longer waiting for receptionist review.");
        }
        if (!isStaff(action.userId())) {
            return ResponseEntity.status(403).body("Only hotel staff can reject a room swap request.");
        }
        swap.setStatus("REJECTED");
        return ResponseEntity.ok(swapRepository.save(swap));
    }

    @PostMapping("/{id}/admin/approve")
    @Transactional
    public ResponseEntity<?> approveRequest(@PathVariable Long id, @RequestBody StaffAction action) {
        if (!isStaff(action.userId())) return ResponseEntity.status(403).body("Hotel staff access is required.");
        Optional<RoomSwapRequest> swapOption = swapRepository.findByIdForUpdate(id);
        if (swapOption.isEmpty()) return ResponseEntity.notFound().build();
        RoomSwapRequest swap = swapOption.get();
        boolean pendingWithoutDifference = "PENDING".equals(swap.getStatus())
                && (swap.getAdditionalAmount() == null || swap.getAdditionalAmount() <= 0);
        if (!"ACCEPTED".equals(swap.getStatus()) && !pendingWithoutDifference) {
            return ResponseEntity.badRequest().body("The receptionist must accept the request and any price difference must be paid before the swap is completed.");
        }
        if (swap.getAdditionalAmount() != null && swap.getAdditionalAmount() > 0 && !swap.isAdditionalAmountPaid()) {
            return ResponseEntity.badRequest().body("The room-rate difference must be paid before staff approval.");
        }

        List<Reservation> bookings = lockBookings(swap.getRequesterReservationId(), swap.getTargetReservationId());
        if (!areEligibleForSwap(swap, bookings)) {
            return ResponseEntity.badRequest().body("These bookings or room assignments have changed; this swap cannot be approved.");
        }
        Reservation requester = bookings.get(0);
        Reservation target = bookings.get(1);
        Optional<Room> requesterRoom = findRoom(requester.getRoomId());
        Optional<Room> targetRoom = findRoom(target.getRoomId());
        if (requesterRoom.isEmpty() || targetRoom.isEmpty()
                || "Maintenance".equalsIgnoreCase(requesterRoom.get().getStatus())
                || "Maintenance".equalsIgnoreCase(targetRoom.get().getStatus())) {
            return ResponseEntity.badRequest().body("A room in this request is unavailable for swapping.");
        }
        String requesterRoomId = requester.getRoomId();
        String requesterRoomName = requester.getRoomName();
        if (swap.getAdditionalAmount() != null && swap.getAdditionalAmount() > 0) {
            requester.setAmount((requester.getAmount() == null ? 0 : requester.getAmount()) + swap.getAdditionalAmount());
        }
        requester.setRoomId(target.getRoomId());
        requester.setRoomName(target.getRoomName());
        target.setRoomId(requesterRoomId);
        target.setRoomName(requesterRoomName);
        reservationRepository.saveAll(List.of(requester, target));

        swap.setStatus("ADMIN_APPROVED");
        swapRepository.save(swap);
        swap.setStatus("COMPLETED");
        return ResponseEntity.ok(swapRepository.save(swap));
    }

    @PostMapping("/{id}/admin/reject")
    @Transactional
    public ResponseEntity<?> rejectRequestByAdmin(@PathVariable Long id, @RequestBody StaffAction action) {
        if (!isStaff(action.userId())) return ResponseEntity.status(403).body("Hotel staff access is required.");
        Optional<RoomSwapRequest> swapOption = swapRepository.findByIdForUpdate(id);
        if (swapOption.isEmpty()) return ResponseEntity.notFound().build();
        RoomSwapRequest swap = swapOption.get();
        if (!"ACCEPTED".equals(swap.getStatus())) {
            return ResponseEntity.badRequest().body("Only a guest-accepted request can be rejected by staff.");
        }
        swap.setStatus("ADMIN_REJECTED");
        return ResponseEntity.ok(swapRepository.save(swap));
    }

    private List<Reservation> lockBookings(Long firstId, Long secondId) {
        long lowerId = Math.min(firstId, secondId);
        long higherId = Math.max(firstId, secondId);
        Optional<Reservation> lower = reservationRepository.findByIdForUpdate(lowerId);
        Optional<Reservation> higher = reservationRepository.findByIdForUpdate(higherId);
        if (lower.isEmpty() || higher.isEmpty()) return List.of();
        return firstId < secondId ? List.of(lower.get(), higher.get()) : List.of(higher.get(), lower.get());
    }

    private boolean hasActiveRequest(Long firstBookingId, Long secondBookingId) {
        return swapRepository.existsByRequesterReservationIdAndStatusIn(firstBookingId, ACTIVE_STATUSES)
                || swapRepository.existsByTargetReservationIdAndStatusIn(firstBookingId, ACTIVE_STATUSES)
                || swapRepository.existsByRequesterReservationIdAndStatusIn(secondBookingId, ACTIVE_STATUSES)
                || swapRepository.existsByTargetReservationIdAndStatusIn(secondBookingId, ACTIVE_STATUSES);
    }

    private boolean areEligibleForSwap(Reservation requester, Reservation target) {
        return isEligible(requester) && isEligible(target)
                && !Objects.equals(requester.getRoomId(), target.getRoomId())
                && Objects.equals(requester.getCheckInDate(), target.getCheckInDate())
                && Objects.equals(requester.getCheckOutDate(), target.getCheckOutDate());
    }

    private double calculateAdditionalAmount(Reservation requester, Reservation target,
                                             Room requesterRoom, Room targetRoom) {
        long nights = ChronoUnit.DAYS.between(requester.getCheckInDate(), requester.getCheckOutDate());
        double nightlyDifference = targetRoom.getPrice() - requesterRoom.getPrice();
        return Math.round(Math.max(0, nightlyDifference) * nights * 100.0) / 100.0;
    }

    private boolean areEligibleForSwap(RoomSwapRequest swap, List<Reservation> bookings) {
        if (bookings.size() != 2) return false;
        Reservation requester = bookings.get(0);
        Reservation target = bookings.get(1);
        return areEligibleForSwap(requester, target)
                && Objects.equals(requester.getUserId(), swap.getRequesterUserId())
                && Objects.equals(target.getUserId(), swap.getTargetUserId())
                && Objects.equals(requester.getRoomId(), swap.getRequesterRoomId())
                && Objects.equals(target.getRoomId(), swap.getTargetRoomId());
    }

    private boolean isEligible(Reservation reservation) {
        return reservation != null
                && ("Confirmed".equalsIgnoreCase(reservation.getStatus())
                    || "Checked In".equalsIgnoreCase(reservation.getStatus()))
                && reservation.getRoomId() != null
                && reservation.getCheckInDate() != null
                && reservation.getCheckOutDate() != null
                && reservation.getCheckOutDate().isAfter(reservation.getCheckInDate())
                && reservation.getCheckOutDate().isAfter(LocalDate.now());
    }

    private Optional<Room> findRoom(String roomId) {
        try {
            return roomRepository.findById(Long.parseLong(roomId));
        } catch (NumberFormatException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private Optional<User> findUser(String userId) {
        try {
            return userRepository.findById(Long.parseLong(userId));
        } catch (NumberFormatException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private boolean isGuest(String userId) {
        return findUser(userId)
                .map(user -> "guest".equalsIgnoreCase(user.getRole()))
                .orElseGet(() -> userId != null && userId.startsWith("guest-"));
    }

    private boolean isStaff(String userId) {
        return findUser(userId)
                .map(user -> isStaffRole(user.getRole()))
                .orElse(false);
    }

    private boolean isStaffRole(String role) {
        return "admin".equalsIgnoreCase(role) || "receptionist".equalsIgnoreCase(role);
    }

    private boolean matchesPreference(Room currentRoom, Room targetRoom, Preferences preferences) {
        if (preferences == null) return false;
        String targetDescription = roomDescription(targetRoom);
        if ("Required".equalsIgnoreCase(preferences.balcony())
                && !targetDescription.contains("balcony")) return false;
        if ("Higher".equalsIgnoreCase(preferences.floor())
                && floor(targetRoom) <= floor(currentRoom)) return false;
        if ("Lower".equalsIgnoreCase(preferences.floor())
                && floor(targetRoom) >= floor(currentRoom)) return false;
        if (notAny(preferences.roomType())
                && !equalsIgnoreCase(targetRoom.getType(), preferences.roomType())) return false;
        if (notAny(preferences.bedType())
                && !targetDescription.contains(preferences.bedType().toLowerCase(Locale.ROOT))) return false;
        return !notAny(preferences.view())
                || targetDescription.contains(preferences.view().toLowerCase(Locale.ROOT));
    }

    private String roomDescription(Room room) {
        String amenities = room.getAmenities() == null ? "" : String.join(" ", room.getAmenities());
        return (room.getName() + " " + room.getType() + " " + amenities).toLowerCase(Locale.ROOT);
    }

    private int floor(Room room) {
        String digits = room.getRoomNo() == null ? "" : room.getRoomNo().replaceAll("\\D", "");
        if (digits.isEmpty()) return 0;
        String floorDigits = digits.length() > 2 ? digits.substring(0, digits.length() - 2) : digits;
        try {
            return Integer.parseInt(floorDigits);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private boolean notAny(String value) {
        return value != null && !value.isBlank() && !"Any".equalsIgnoreCase(value);
    }

    private boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    public record Preferences(String balcony, String floor, String roomType, String bedType, String view) {}

    public record CreateSwapRequest(Long requesterBookingId, Long targetBookingId, String requesterGuestId,
                                    Preferences preferences) {}

    public record GuestAction(String userId) {}

    public record PayDifferenceAction(String userId, Double amount) {}

    public record StaffAction(String userId) {}
}
