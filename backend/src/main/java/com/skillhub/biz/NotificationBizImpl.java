package com.skillhub.biz;

import com.skillhub.security.PermissionService;
import com.skillhub.domain.Notification;
import com.skillhub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationBizImpl implements NotificationBiz {

    private final NotificationRepository notificationRepository;
    private final PermissionService permissionService;

    @Override
    public List<Notification> list() {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(permissionService.currentUserId());
    }

    @Override
    public long unreadCount() {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(permissionService.currentUserId()).size();
    }

    @Override
    @Transactional
    public void markRead(Long id) {
        Notification n = notificationRepository.findById(id).orElseThrow(() -> new BizException("通知不存在"));
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Override
    @Transactional
    public void markAllRead() {
        var unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(permissionService.currentUserId());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}