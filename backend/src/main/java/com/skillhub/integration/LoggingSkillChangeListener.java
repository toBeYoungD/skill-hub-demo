package com.skillhub.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoggingSkillChangeListener implements SkillChangeListener {

    @Override
    public void onSkillDelisted(String skillName, String reason) {
        log.info("[SkillChange] DELISTED: skillName={}, reason={}", skillName, reason);
    }

    @Override
    public void onSkillUpgraded(String skillName, String latestVersion) {
        log.info("[SkillChange] UPGRADED: skillName={}, latestVersion={}", skillName, latestVersion);
    }
}