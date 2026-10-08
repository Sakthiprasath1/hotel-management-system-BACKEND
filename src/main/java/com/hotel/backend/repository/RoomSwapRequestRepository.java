package com.hotel.backend.repository;

import com.hotel.backend.model.RoomSwapRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface RoomSwapRequestRepository extends JpaRepository<RoomSwapRequest, Long> {
    boolean existsByRequesterReservationIdAndStatusIn(Long reservationId, Iterable<String> statuses);

    boolean existsByTargetReservationIdAndStatusIn(Long reservationId, Iterable<String> statuses);

    List<RoomSwapRequest> findByRequesterUserIdOrTargetUserId(String requesterUserId, String targetUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT swap FROM RoomSwapRequest swap WHERE swap.id = :id")
    Optional<RoomSwapRequest> findByIdForUpdate(@Param("id") Long id);
}
