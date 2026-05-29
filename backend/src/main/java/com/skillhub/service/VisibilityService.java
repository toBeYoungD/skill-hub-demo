package com.skillhub.service;

import com.skillhub.security.PermissionService;
import com.skillhub.domain.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisibilityService {

    private final PermissionService permissionService;

    @Value("${skill.visibility.enabled:false}")
    private boolean visibilityEnabled;

    public boolean isVisibleToCurrentUser(Skill skill) {
        if (!visibilityEnabled) return true;
        if (skill.getStatus() == Skill.SkillStatus.PUBLISHED)
            return permissionService.isVisibleTo(permissionService.currentUserId(),
                    skill.getVisibilityType(), skill.getVisibilityConfig());
        return false;
    }
}