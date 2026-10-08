package com.hotel.backend.config;

import com.hotel.backend.model.*;
import com.hotel.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(
            UserRepository userRepository,
            RoomRepository roomRepository,
            StaffRepository staffRepository,
            JdbcTemplate jdbcTemplate) {
        return args -> {
            migrateRoomAmenities(jdbcTemplate);
            migrateRoomSwapRequests(jdbcTemplate);

            // Seed Admin & Users if empty
            if (userRepository.count() == 0) {
                userRepository.save(User.builder().name("SAKTHI (Admin)").email("vibe@gmail.com").password("123456").role("admin").phone("9876543210").build());
                userRepository.save(User.builder().name("ALICE (Receptionist)").email("receptionist@vibe.com").password("1234").role("receptionist").phone("9876543219").build());
                userRepository.save(User.builder().name("GURU (Guest)").email("super@gmail.com").password("super").role("guest").phone("9876543211").build());
            }

            // Seed Rooms if empty
            if (roomRepository.count() == 0) {
                roomRepository.save(Room.builder().roomNo("R-101").name("Standard Comfort").type("Standard").status("Available").price(3500.0).capacity(2).extraGuestRate(500.0).amenities(List.of("AC", "Wifi", "Work Desk")).image("/assets/rooms/room_standard.png").build());
                roomRepository.save(Room.builder().roomNo("R-102").name("Standard Twin").type("Standard").status("Available").price(3800.0).capacity(2).extraGuestRate(500.0).amenities(List.of("AC", "Wifi", "TV")).image("/assets/rooms/room_standard.png").build());
                roomRepository.save(Room.builder().roomNo("R-201").name("Deluxe Garden View").type("Deluxe").status("Available").price(5500.0).capacity(4).extraGuestRate(750.0).amenities(List.of("AC", "Wifi", "Mini Bar", "Garden View")).image("/assets/rooms/room_deluxe.png").build());
                roomRepository.save(Room.builder().roomNo("R-301").name("Executive Business Suite").type("Executive").status("Available").price(8500.0).capacity(4).extraGuestRate(1000.0).amenities(List.of("AC", "Wifi", "Prime Desk")).image("/assets/rooms/room_executive.png").build());
                roomRepository.save(Room.builder().roomNo("R-401").name("Royal Crown Suite").type("Royal").status("Available").price(18000.0).capacity(6).extraGuestRate(1500.0).amenities(List.of("AC", "Wifi", "Private Balcony", "Jacuzzi")).image("/assets/rooms/room_royal.png").build());
            } else {
                roomRepository.findAll().forEach(room -> {
                    boolean updated = false;
                    if (room.getExtraGuestRate() == null) {
                        room.setExtraGuestRate(defaultExtraGuestRate(room.getType()));
                        updated = true;
                    }
                    if (room.getCapacity() != null && room.getCapacity() == 2) {
                        Integer familyCapacity = switch (room.getRoomNo()) {
                            case "R-201", "R-301" -> 4;
                            case "R-401" -> 6;
                            default -> null;
                        };
                        if (familyCapacity != null) {
                            room.setCapacity(familyCapacity);
                            updated = true;
                        }
                    }
                    if (updated) roomRepository.save(room);
                });
            }

            // Seed Staff if empty
            if (staffRepository.count() == 0) {
                staffRepository.save(Staff.builder().name("AMMU").role("Receptionist").shift("Morning").status("Active").build());
                staffRepository.save(Staff.builder().name("HARI").role("Housekeeping").shift("Evening").status("Active").build());
            }
        };
    }

    private static void migrateRoomAmenities(JdbcTemplate jdbcTemplate) {
        Integer legacyTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = 'room_amenities'",
                Integer.class);

        if (legacyTableCount != null && legacyTableCount > 0) {
            jdbcTemplate.update("""
                    UPDATE rooms r
                    JOIN (
                        SELECT room_id, JSON_ARRAYAGG(amenity) AS amenities
                        FROM room_amenities
                        GROUP BY room_id
                    ) legacy ON legacy.room_id = r.id
                    SET r.amenities = legacy.amenities
                    WHERE r.amenities IS NULL
                    """);
            jdbcTemplate.execute("DROP TABLE room_amenities");
        }
    }

    private static void migrateRoomSwapRequests(JdbcTemplate jdbcTemplate) {
        Integer legacyColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'room_swap_requests' " +
                        "AND column_name IN ('requester_reservation_id', 'target_reservation_id')",
                Integer.class);

        if (legacyColumnCount != null && legacyColumnCount == 2) {
            jdbcTemplate.update("""
                    UPDATE room_swap_requests s
                    LEFT JOIN reservations requester ON requester.id = s.requester_reservation_id
                    LEFT JOIN reservations target ON target.id = s.target_reservation_id
                    SET s.requester_room_id = COALESCE(s.requester_room_id, requester.room_id),
                        s.target_room_id = COALESCE(s.target_room_id, target.room_id),
                        s.status = CASE
                            WHEN s.status = 'APPROVED' THEN 'COMPLETED'
                            WHEN s.status = 'DECLINED' THEN 'REJECTED'
                            WHEN s.status = 'PENDING' AND s.target_accepted = 1 THEN 'ACCEPTED'
                            ELSE s.status
                        END
                    """);
        }
    }

    private static double defaultExtraGuestRate(String type) {
        if (type == null) return 500.0;
        return switch (type) {
            case "Deluxe" -> 750.0;
            case "Executive" -> 1000.0;
            case "Royal" -> 1500.0;
            default -> 500.0;
        };
    }
}
