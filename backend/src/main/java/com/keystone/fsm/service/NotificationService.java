package com.keystone.fsm.service;

import com.keystone.fsm.domain.enums.NotificationType;
import com.keystone.fsm.domain.enums.Role;
import com.keystone.fsm.domain.model.Notification;
import com.keystone.fsm.domain.model.User;
import com.keystone.fsm.domain.model.WorkOrder;
import com.keystone.fsm.dto.response.NotificationResponse;
import com.keystone.fsm.exception.ResourceNotFoundException;
import com.keystone.fsm.repository.NotificationRepository;
import com.keystone.fsm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public void notifyUser(String userId, NotificationType type, String title, String message, WorkOrder wo) {
        if (userId == null) {
            return;
        }
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .workOrderId(wo.getId())
                .workOrderCode(wo.getCode())
                .read(false)
                .build();
        notificationRepository.save(n);
        log.info("Notification [{}] sent to user {} for work order {}", type, userId, wo.getCode());
    }

    public void notifyRole(Role role, NotificationType type, String title, String message, WorkOrder wo) {
        List<User> recipients = userRepository.findByRoleAndEnabledTrue(role);
        recipients.forEach(u -> notifyUser(u.getId(), type, title, message, wo));
    }

    /** Sends at most one notification of a given type per work order to each manager. */
    public void notifyManagersOnce(String typeKey, NotificationType type, String title, String message, WorkOrder wo) {
        for (User manager : userRepository.findByRoleAndEnabledTrue(Role.MANAGER)) {
            if (notificationRepository.existsByWorkOrderIdAndTypeAndUserId(wo.getId(), type, manager.getId())) {
                continue;
            }
            notifyUser(manager.getId(), type, title, message, wo);
        }
    }

    public Page<NotificationResponse> list(String userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(NotificationResponse::from);
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markRead(String id, String userId) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (!n.isRead()) {
            n.setRead(true);
            notificationRepository.save(n);
        }
    }

    public void markAllRead(String userId) {
        Query q = new Query(Criteria.where("userId").is(userId).and("read").is(false));
        mongoTemplate.updateMulti(q, new Update().set("read", true), Notification.class);
    }
}
