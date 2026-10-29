package com.keystone.fsm.repository;

import com.keystone.fsm.domain.model.Part;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PartRepository extends MongoRepository<Part, String> {

    Optional<Part> findByPartNumber(String partNumber);

    boolean existsByPartNumber(String partNumber);

    Page<Part> findByNameContainingIgnoreCaseOrPartNumberContainingIgnoreCase(
            String name, String partNumber, Pageable pageable);
}
