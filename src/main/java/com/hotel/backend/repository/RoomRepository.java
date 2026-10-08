package com.hotel.backend.repository;

import com.hotel.backend.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByType(String type);
    List<Room> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select room from Room room order by room.id")
    List<Room> findAllForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select room from Room room where room.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
