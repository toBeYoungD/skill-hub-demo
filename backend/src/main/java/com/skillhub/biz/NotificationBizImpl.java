package com.skillhub.biz;

import com.skillhub.dao.NotificationMapper;
import com.skillhub.domain.Notification;
import com.skillhub.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationBizImpl implements NotificationBiz {

    private final NotificationMapper notificationMapper;
    private final PermissionService permissionService;

    @Override
    public List<Notification> list() {
        return notificationMapper.selectByUserIdOrderByCreatedAtDesc(permissionService.currentUserId());
    }

    @Override
    public long unreadCount() {
        return notificationMapper.selectByUserIdAndReadFalse(permissionService.currentUserId()).size();
    }

    @Override
    @Transactional
    public void markRead(Long id) {
        notificationMapper.updateRead(id, true);
    }

    @Override
    @Transactional
    public void markAllRead() {
        notificationMapper.markAllRead(permissionService.currentUserId());
    }
}