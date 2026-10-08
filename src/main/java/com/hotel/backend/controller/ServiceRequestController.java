package com.hotel.backend.controller;

import com.hotel.backend.model.ServiceRequest;
import com.hotel.backend.repository.ServiceRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-requests")
@CrossOrigin(origins = "*")
public class ServiceRequestController {

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @GetMapping
    public List<ServiceRequest> getAllRequests() {
        return serviceRequestRepository.findAll();
    }

    @PostMapping
    public ServiceRequest createRequest(@RequestBody ServiceRequest request) {
        if (request.getStatus() == null) {
            request.setStatus("Pending");
        }
        return serviceRequestRepository.save(request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceRequest> updateRequestStatus(@PathVariable Long id, @RequestBody ServiceRequest details) {
        return serviceRequestRepository.findById(id).map(req -> {
            req.setStatus(details.getStatus());
            return ResponseEntity.ok(serviceRequestRepository.save(req));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRequest(@PathVariable Long id) {
        if (serviceRequestRepository.existsById(id)) {
            serviceRequestRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}

