package com.keystone.fsm.domain.model;

import com.keystone.fsm.domain.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notifications")
@CompoundIndex(name = "notif_user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    /** Recipient user id */
    private String userId;

    private NotificationType type;
    private String title;
    private String message;

    private String workOrderId;
    private String workOrderCode;

    private boolean read;

    @CreatedDate
    private Instant createdAt;
}
