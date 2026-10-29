package com.keystone.fsm.service;

import com.keystone.fsm.domain.model.Part;
import com.keystone.fsm.dto.request.CreatePartRequest;
import com.keystone.fsm.dto.response.PartResponse;
import com.keystone.fsm.exception.DuplicateResourceException;
import com.keystone.fsm.exception.ResourceNotFoundException;
import com.keystone.fsm.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PartService {

    private final PartRepository partRepository;

    public PartResponse createPart(CreatePartRequest request) {
        if (partRepository.existsByPartNumber(request.partNumber())) {
            throw new DuplicateResourceException("Part already exists: " + request.partNumber());
        }
        Part part = Part.builder()
                .partNumber(request.partNumber())
                .name(request.name())
                .description(request.description())
                .unitOfMeasure(request.unitOfMeasure())
                .quantityInStock(request.quantityInStock())
                .reorderLevel(request.reorderLevel())
                .unitCost(request.unitCost())
                .build();
        return toResponse(partRepository.save(part));
    }

    public PartResponse getPartById(String id) {
        return toResponse(getPartEntity(id));
    }

    public Part getPartEntity(String id) {
        return partRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Part", id));
    }

    public Page<PartResponse> getParts(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return partRepository
                    .findByNameContainingIgnoreCaseOrPartNumberContainingIgnoreCase(search, search, pageable)
                    .map(this::toResponse);
        }
        return partRepository.findAll(pageable).map(this::toResponse);
    }

    public PartResponse updatePart(String id, CreatePartRequest request) {
        Part part = getPartEntity(id);
        part.setName(request.name());
        part.setDescription(request.description());
        part.setUnitOfMeasure(request.unitOfMeasure());
        part.setQuantityInStock(request.quantityInStock());
        part.setReorderLevel(request.reorderLevel());
        part.setUnitCost(request.unitCost());
        return toResponse(partRepository.save(part));
    }

    private PartResponse toResponse(Part p) {
        return new PartResponse(
                p.getId(), p.getPartNumber(), p.getName(),
                p.getDescription(), p.getUnitOfMeasure(),
                p.getQuantityInStock(), p.getReorderLevel(),
                p.getUnitCost(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
