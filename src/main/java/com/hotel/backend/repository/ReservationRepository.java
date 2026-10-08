package com.hotel.backend.repository;

import com.hotel.backend.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUserId(String userId);

    List<Reservation> findByGuestEmailIgnoreCase(String guestEmail);

    @Query("SELECT reservation FROM Reservation reservation "
            + "WHERE reservation.roomId = :roomId "
            + "AND reservation.checkInDate < :checkOutDate "
            + "AND reservation.checkOutDate > :checkInDate "
            + "AND COALESCE(LOWER(TRIM(reservation.status)), '') NOT IN ('cancelled', 'checked out')")
    List<Reservation> findOverlappingReservations(@Param("roomId") String roomId,
                                                   @Param("checkInDate") LocalDate checkInDate,
                                                   @Param("checkOutDate") LocalDate checkOutDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reservation FROM Reservation reservation WHERE reservation.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}
