package com.hotel.backend.controller;

import com.hotel.backend.model.Staff;
import com.hotel.backend.repository.StaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@CrossOrigin(origins = "*")
public class StaffController {

    @Autowired
    private StaffRepository staffRepository;

    @GetMapping
    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }

    @PostMapping
    public Staff createStaff(@RequestBody Staff staff) {
        return staffRepository.save(staff);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Staff> updateStaff(@PathVariable Long id, @RequestBody Staff details) {
        return staffRepository.findById(id).map(s -> {
            s.setName(details.getName());
            s.setRole(details.getRole());
            s.setShift(details.getShift());
            s.setStatus(details.getStatus());
            return ResponseEntity.ok(staffRepository.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStaff(@PathVariable Long id) {
        if (staffRepository.existsById(id)) {
            staffRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}

