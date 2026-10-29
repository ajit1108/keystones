package com.keystone.fsm.dto.response;

import com.keystone.fsm.domain.enums.NotificationType;
import com.keystone.fsm.domain.model.Notification;

import java.time.Instant;

public record NotificationResponse(
    String id,
    NotificationType type,
    String title,
    String message,
    String workOrderId,
    String workOrderCode,
    boolean read,
    Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
            n.getId(), n.getType(), n.getTitle(), n.getMessage(),
            n.getWorkOrderId(), n.getWorkOrderCode(), n.isRead(), n.getCreatedAt());
    }
}
