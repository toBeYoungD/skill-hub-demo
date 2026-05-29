package com.skillhub.service;

/**
 * 消息推送服务接口。Demo 实现在内存中存储通知。公司落地时对接邮件/IM 系统。
 */
public interface NotificationService {
    void notify(String userId, String event, Long skillId, String skillName, String message);
}