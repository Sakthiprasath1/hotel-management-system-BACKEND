package com.hotel.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "staff")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String role; // Receptionist, Housekeeping, Manager, Chef

    private String shift; // Morning, Evening, Night

    private String status; // Active, On Leave, Inactive
}

