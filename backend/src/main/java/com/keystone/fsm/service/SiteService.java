package com.keystone.fsm.service;

import com.keystone.fsm.domain.enums.Role;
import com.keystone.fsm.domain.model.Customer;
import com.keystone.fsm.domain.model.Site;
import com.keystone.fsm.dto.request.CreateSiteRequest;
import com.keystone.fsm.dto.response.SiteResponse;
import com.keystone.fsm.exception.AccessDeniedException;
import com.keystone.fsm.exception.DuplicateResourceException;
import com.keystone.fsm.exception.ResourceNotFoundException;
import com.keystone.fsm.repository.SiteRepository;
import com.keystone.fsm.security.KeystonePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;
    private final CustomerService customerService;

    public SiteResponse createSite(String customerId, CreateSiteRequest request) {
        Customer customer = customerService.getCustomerEntity(customerId);
        if (siteRepository.existsByCustomerIdAndName(customerId, request.name())) {
            throw new DuplicateResourceException("Site '" + request.name() + "' already exists for this customer");
        }
        Site site = Site.builder()
                .customerId(customerId)
                .name(request.name())
                .address(request.address())
                .city(request.city())
                .state(request.state())
                .country(request.country())
                .postalCode(request.postalCode())
                .contactName(request.contactName())
                .contactPhone(request.contactPhone())
                .active(true)
                .build();
        return toResponse(siteRepository.save(site), customer.getName());
    }

    public SiteResponse updateSite(String customerId, String siteId, CreateSiteRequest request) {
        Customer customer = customerService.getCustomerEntity(customerId);
        Site site = getSiteEntity(siteId);
        if (!site.getCustomerId().equals(customerId)) {
            throw new ResourceNotFoundException("Site", siteId);
        }
        if (!site.getName().equals(request.name())
                && siteRepository.existsByCustomerIdAndName(customerId, request.name())) {
            throw new DuplicateResourceException("Site '" + request.name() + "' already exists for this customer");
        }
        site.setName(request.name());
        site.setAddress(request.address());
        site.setCity(request.city());
        site.setState(request.state());
        site.setCountry(request.country());
        site.setPostalCode(request.postalCode());
        site.setContactName(request.contactName());
        site.setContactPhone(request.contactPhone());
        return toResponse(siteRepository.save(site), customer.getName());
    }

    public Page<SiteResponse> getSitesByCustomer(String customerId, String search,
                                                 Pageable pageable, KeystonePrincipal actor) {
        assertScope(actor, customerId);
        Customer customer = customerService.getCustomerEntity(customerId);
        Page<Site> sites;
        if (search != null && !search.isBlank()) {
            sites = siteRepository.findByCustomerIdAndNameContainingIgnoreCase(customerId, search, pageable);
        } else {
            sites = siteRepository.findByCustomerId(customerId, pageable);
        }
        return sites.map(s -> toResponse(s, customer.getName()));
    }

    public SiteResponse getSiteById(String customerId, String siteId, KeystonePrincipal actor) {
        assertScope(actor, customerId);
        Site site = getSiteEntity(siteId);
        if (!site.getCustomerId().equals(customerId)) {
            throw new ResourceNotFoundException("Site", siteId);
        }
        Customer customer = customerService.getCustomerEntity(site.getCustomerId());
        return toResponse(site, customer.getName());
    }

    public Site getSiteEntity(String siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId));
    }

    public List<SiteResponse> getAllSitesByCustomer(String customerId, KeystonePrincipal actor) {
        assertScope(actor, customerId);
        Customer customer = customerService.getCustomerEntity(customerId);
        return siteRepository.findByCustomerId(customerId).stream()
                .map(s -> toResponse(s, customer.getName()))
                .toList();
    }

    private void assertScope(KeystonePrincipal actor, String customerId) {
        if (actor.getRole() == Role.CUSTOMER && !customerId.equals(actor.getCustomerId())) {
            throw new AccessDeniedException("Customers can only read their own sites");
        }
    }

    private SiteResponse toResponse(Site s, String customerName) {
        return new SiteResponse(
                s.getId(), s.getCustomerId(), customerName,
                s.getName(), s.getAddress(), s.getCity(),
                s.getState(), s.getCountry(), s.getPostalCode(),
                s.getContactName(), s.getContactPhone(),
                s.isActive(), s.getCreatedAt());
    }
}
