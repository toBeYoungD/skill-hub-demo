package com.skillhub.service;

import com.skillhub.domain.Notification;
import com.skillhub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public void notify(String userId, String event, Long skillId, String skillName, String message) {
        log.info("[通知] userId={}, event={}, skillId={}, message={}", userId, event, skillId, message);
        Notification n = new Notification();
        n.setUserId(userId);
        n.setEvent(event);
        n.setSkillId(skillId);
        n.setSkillName(skillName);
        n.setMessage(message);
        notificationRepository.save(n);
    }
}