package com.skillhub.integration;

/**
 * 技能变更事件监听器。Demo 实现仅打日志。公司落地时对接消息队列。
 */
public interface SkillChangeListener {
    void onSkillDelisted(String skillName, String reason);
    void onSkillUpgraded(String skillName, String latestVersion);
}