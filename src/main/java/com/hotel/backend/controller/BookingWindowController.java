package com.hotel.backend.controller;

import com.hotel.backend.model.BookingWindow;
import com.hotel.backend.repository.BookingWindowRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/booking-window")
@CrossOrigin(origins = "*")
public class BookingWindowController {
    private static final Long WINDOW_ID = 1L;

    private final BookingWindowRepository bookingWindowRepository;

    public BookingWindowController(BookingWindowRepository bookingWindowRepository) {
        this.bookingWindowRepository = bookingWindowRepository;
    }

    @GetMapping
    @Transactional
    public BookingWindow getBookingWindow() {
        return getOrCreateBookingWindow();
    }

    @PostMapping("/open-next")
    @Transactional
    public ResponseEntity<?> openNextWindow() {
        BookingWindow window = getOrCreateBookingWindow();
        if (!LocalDate.now().isAfter(window.getBookingEndDate())) {
            return ResponseEntity.status(409).body("The current booking window must finish before opening the next six months.");
        }

        LocalDate nextStart = window.getBookingEndDate().plusDays(1);
        window.setBookingStartDate(nextStart);
        window.setBookingEndDate(nextStart.plusMonths(6).minusDays(1));
        return ResponseEntity.ok(bookingWindowRepository.save(window));
    }

    private BookingWindow getOrCreateBookingWindow() {
        return bookingWindowRepository.findById(WINDOW_ID).orElseGet(() -> {
            LocalDate startDate = LocalDate.now();
            return bookingWindowRepository.save(BookingWindow.builder()
                    .id(WINDOW_ID)
                    .bookingStartDate(startDate)
                    .bookingEndDate(startDate.plusMonths(6).minusDays(1))
                    .build());
        });
    }
}
