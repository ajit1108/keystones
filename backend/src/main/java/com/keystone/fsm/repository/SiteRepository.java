package com.keystone.fsm.repository;

import com.keystone.fsm.domain.model.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends MongoRepository<Site, String> {

    List<Site> findByCustomerId(String customerId);

    Page<Site> findByCustomerId(String customerId, Pageable pageable);

    Page<Site> findByCustomerIdAndNameContainingIgnoreCase(String customerId, String name, Pageable pageable);

    Optional<Site> findByCustomerIdAndName(String customerId, String name);

    boolean existsByCustomerIdAndName(String customerId, String name);
}
