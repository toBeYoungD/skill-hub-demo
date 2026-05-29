package com.skillhub.biz;

import com.skillhub.domain.Notification;
import java.util.List;

public interface NotificationBiz {
    List<Notification> list();
    long unreadCount();
    void markRead(Long id);
    void markAllRead();
}