package com.skillhub.repository;

import com.skillhub.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId);
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);
}