package com.hotel.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_swap_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomSwapRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requesterReservationId;

    @Column(nullable = false)
    private Long targetReservationId;

    @Column(nullable = false)
    private String requesterUserId;

    @Column(nullable = false)
    private String targetUserId;

    private String requesterRoomId;

    private String targetRoomId;

    private String preferredBalcony;

    private String preferredFloor;

    private String preferredRoomType;

    private String preferredBedType;

    private String preferredView;

    @Builder.Default
    @Column(nullable = false)
    private Double additionalAmount = 0.0;

    @Builder.Default
    @Column(nullable = false)
    private boolean additionalAmountPaid = false;

    @Column(nullable = false)
    private boolean targetAccepted;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
