package com.keystone.fsm.repository;

import com.keystone.fsm.domain.enums.NotificationType;
import com.keystone.fsm.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndReadFalse(String userId);

    Optional<Notification> findByIdAndUserId(String id, String userId);

    boolean existsByWorkOrderIdAndType(NotificationType type);

    boolean existsByWorkOrderIdAndTypeAndUserId(String workOrderId, NotificationType type, String userId);
}
