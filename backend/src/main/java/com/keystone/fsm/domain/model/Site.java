package com.keystone.fsm.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "sites")
@CompoundIndexes({
    @CompoundIndex(name = "site_customer_idx", def = "{'customerId': 1, 'name': 1}", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Site {

    @Id
    private String id;

    /** Reference to parent customer */
    private String customerId;

    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String contactName;
    private String contactPhone;

    private boolean active;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
