package com.hotel.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String roomNo;

    private String name;

    private String type; // Standard, Deluxe, Executive, Royal

    private String status; // Available, Occupied, Maintenance

    private Double price;

    private Integer capacity;

    private Double extraGuestRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> amenities;

    private String image;
}
