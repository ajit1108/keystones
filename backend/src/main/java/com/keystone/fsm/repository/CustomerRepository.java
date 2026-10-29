package com.keystone.fsm.repository;

import com.keystone.fsm.domain.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CustomerRepository extends MongoRepository<Customer, String> {

    Optional<Customer> findByName(String name);

    boolean existsByName(String name);

    Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Customer> findByActive(boolean active, Pageable pageable);
}
