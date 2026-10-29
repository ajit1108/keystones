package com.keystone.fsm.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "parts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Part {

    @Id
    private String id;

    @Indexed(unique = true)
    private String partNumber;

    private String name;
    private String description;
    private String unitOfMeasure;

    private int quantityInStock;
    private int reorderLevel;

    private BigDecimal unitCost;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
