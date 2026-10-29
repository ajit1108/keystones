package com.keystone.fsm.controller;

import com.keystone.fsm.dto.response.NotificationResponse;
import com.keystone.fsm.security.KeystonePrincipal;
import com.keystone.fsm.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the current user")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List my notifications, newest first")
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal KeystonePrincipal principal) {
        return ResponseEntity.ok(notificationService.list(principal.getId(), unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count for the badge")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal KeystonePrincipal principal) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(principal.getId())));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public ResponseEntity<Void> markRead(@PathVariable String id,
                                         @AuthenticationPrincipal KeystonePrincipal principal) {
        notificationService.markRead(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal KeystonePrincipal principal) {
        notificationService.markAllRead(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
