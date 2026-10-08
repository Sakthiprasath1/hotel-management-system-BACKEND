package com.hotel.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "booking_window")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingWindow {
    @Id
    private Long id;

    private LocalDate bookingStartDate;

    private LocalDate bookingEndDate;
}
