package com.skillhub.controller;

import com.skillhub.auth.PermissionService;
import com.skillhub.entity.Notification;
import com.skillhub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listNotifications() {
        String userId = permissionService.currentUserId();
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", notifications);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount() {
        String userId = permissionService.currentUserId();
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", unread.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id) {
        Notification n = notificationRepository.findById(id).orElseThrow(() -> new RuntimeException("通知不存在"));
        n.setRead(true);
        notificationRepository.save(n);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        String userId = permissionService.currentUserId();
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}