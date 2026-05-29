package com.skillhub.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Slf4j
public class DemoPermissionService implements PermissionService {

    private static final String HEADER = "X-User-Id";
    private static final String DEFAULT = "demo-user";

    @Override
    public String currentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            String uid = req.getHeader(HEADER);
            if (uid != null && !uid.isBlank()) return uid;
        }
        return DEFAULT;
    }

    @Override
    public boolean isAdmin() { return true; }

    @Override
    public boolean isVisibleTo(String userId, String visibilityType, String visibilityConfig) {
        if ("PUBLIC".equals(visibilityType)) return true;
        if ("USER_LIST".equals(visibilityType) && visibilityConfig != null)
            return visibilityConfig.contains("\"" + userId + "\"");
        return true;
    }
}