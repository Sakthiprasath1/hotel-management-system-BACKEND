package com.hotel.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String guestName;

    private String roomNo;

    private String type; // Housekeeping, Room Service, Maintenance

    private String status; // Pending, In Progress, Completed

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

