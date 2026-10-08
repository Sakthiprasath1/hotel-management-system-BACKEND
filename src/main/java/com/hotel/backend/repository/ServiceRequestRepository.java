package com.hotel.backend.repository;

import com.hotel.backend.model.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByUserId(String userId);
}

