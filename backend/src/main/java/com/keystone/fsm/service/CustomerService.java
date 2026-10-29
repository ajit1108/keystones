package com.keystone.fsm.service;

import com.keystone.fsm.domain.model.Customer;
import com.keystone.fsm.dto.request.CreateCustomerRequest;
import com.keystone.fsm.dto.response.CustomerResponse;
import com.keystone.fsm.exception.DuplicateResourceException;
import com.keystone.fsm.exception.ResourceNotFoundException;
import com.keystone.fsm.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        if (customerRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Customer already exists with name: " + request.name());
        }
        Customer customer = Customer.builder()
                .name(request.name())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .address(request.address())
                .active(true)
                .build();
        return toResponse(customerRepository.save(customer));
    }

    public CustomerResponse getCustomerById(String id) {
        return toResponse(getCustomerEntity(id));
    }

    public CustomerResponse getCustomerForAccount(String linkedCustomerId) {
        if (linkedCustomerId == null || linkedCustomerId.isBlank()) {
            throw new com.keystone.fsm.exception.BusinessRuleException(
                    "This account is not linked to a customer organisation");
        }
        return toResponse(getCustomerEntity(linkedCustomerId));
    }

    public Customer getCustomerEntity(String id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    public Page<CustomerResponse> getCustomers(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return customerRepository.findByNameContainingIgnoreCase(search, pageable).map(this::toResponse);
        }
        return customerRepository.findAll(pageable).map(this::toResponse);
    }

    public CustomerResponse updateCustomer(String id, CreateCustomerRequest request) {
        Customer customer = getCustomerEntity(id);
        if (!customer.getName().equals(request.name()) && customerRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Customer name already taken: " + request.name());
        }
        customer.setName(request.name());
        customer.setContactEmail(request.contactEmail());
        customer.setContactPhone(request.contactPhone());
        customer.setAddress(request.address());
        return toResponse(customerRepository.save(customer));
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getName(), c.getContactEmail(),
                c.getContactPhone(), c.getAddress(),
                c.isActive(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
