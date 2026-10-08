package com.hotel.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String guestName;

    private String dates;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    private String roomId;

    private String roomName;

    private Integer guestCount;

    private String guestEmail;

    private String guestPhone;

    private String status; // Confirmed, Cancelled, Pending

    private Double amount;

    private String paymentStatus; // Paid, Refunded, Pending
}
