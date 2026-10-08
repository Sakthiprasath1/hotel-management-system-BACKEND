package com.hotel.backend.controller;

import com.hotel.backend.model.Room;
import com.hotel.backend.model.User;
import com.hotel.backend.repository.ReservationRepository;
import com.hotel.backend.repository.RoomRepository;
import com.hotel.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @GetMapping
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @GetMapping("/available")
    public ResponseEntity<?> getAvailableRooms(@RequestParam LocalDate checkInDate,
                                               @RequestParam LocalDate checkOutDate) {
        if (!checkOutDate.isAfter(checkInDate)) {
            return ResponseEntity.badRequest().body("Check-out must be after check-in.");
        }
        List<Room> availableRooms = roomRepository.findAll().stream()
                .filter(room -> !"Maintenance".equalsIgnoreCase(room.getStatus()))
                .filter(room -> reservationRepository.findOverlappingReservations(
                        room.getId().toString(), checkInDate, checkOutDate).isEmpty())
                .toList();
        return ResponseEntity.ok(availableRooms);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoomById(@PathVariable Long id) {
        return roomRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Room createRoom(@RequestBody Room room) {
        return roomRepository.save(room);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Room> updateRoom(@PathVariable Long id, @RequestBody Room roomDetails) {
        return roomRepository.findById(id).map(room -> {
            room.setRoomNo(roomDetails.getRoomNo());
            room.setName(roomDetails.getName());
            room.setType(roomDetails.getType());
            room.setStatus(roomDetails.getStatus());
            room.setPrice(roomDetails.getPrice());
            room.setCapacity(roomDetails.getCapacity());
            room.setExtraGuestRate(roomDetails.getExtraGuestRate());
            room.setAmenities(roomDetails.getAmenities());
            room.setImage(roomDetails.getImage());
            return ResponseEntity.ok(roomRepository.save(room));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/make-available")
    @Transactional
    public ResponseEntity<?> makeRoomAvailable(@PathVariable Long id, @RequestParam String userId) {
        if (!isStaff(userId)) {
            return ResponseEntity.status(403).body("Hotel staff access is required.");
        }
        Optional<Room> roomOption = roomRepository.findByIdForUpdate(id);
        if (roomOption.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Room room = roomOption.get();
        room.setStatus("Available");
        return ResponseEntity.ok(roomRepository.save(room));
    }

    @PostMapping("/make-available-all")
    @Transactional
    public ResponseEntity<?> makeAllRoomsAvailable(@RequestParam String userId) {
        if (!isStaff(userId)) {
            return ResponseEntity.status(403).body("Hotel staff access is required.");
        }
        List<Room> rooms = roomRepository.findAllForUpdate();
        rooms.forEach(room -> room.setStatus("Available"));
        return ResponseEntity.ok(roomRepository.saveAll(rooms));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        if (roomRepository.existsById(id)) {
            roomRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private boolean isStaff(String userId) {
        try {
            return userRepository.findById(Long.parseLong(userId))
                    .map(user -> "admin".equalsIgnoreCase(user.getRole())
                            || "receptionist".equalsIgnoreCase(user.getRole()))
                    .orElse(false);
        } catch (NumberFormatException | NullPointerException exception) {
            return false;
        }
    }
}
