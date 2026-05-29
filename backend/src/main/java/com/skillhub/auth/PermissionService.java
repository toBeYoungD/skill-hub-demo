package com.skillhub.auth;

/**
 * 权限服务接口。
 * Demo 实现：从请求头 X-User-Id 获取用户，所有人均为管理员。
 * 公司落地时：实现此接口对接公司认证框架。
 */
public interface PermissionService {
    String currentUserId();
    boolean isAdmin();
    boolean isVisibleTo(String userId, String visibilityType, String visibilityConfig);
}