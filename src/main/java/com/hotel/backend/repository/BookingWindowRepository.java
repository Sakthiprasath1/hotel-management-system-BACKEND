package com.hotel.backend.repository;

import com.hotel.backend.model.BookingWindow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingWindowRepository extends JpaRepository<BookingWindow, Long> {
}
