package com.skillhub.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class Skill {

    private Long id;
    private String name;
    private String description;
    private String packageUrl;
    private SkillStatus status = SkillStatus.DRAFT;
    private String developer;
    private Integer downloadCount = 0;
    private Integer useCount = 0;

    // 可见性
    private String visibilityType = "PUBLIC";
    private String visibilityConfig;

    // 逻辑删除
    private Boolean delisted = false;
    private String delistedReason;
    private LocalDateTime delistedAt;
    private String delistedBy;

    private List<SkillVersion> versions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastPublishedAt;

    public enum SkillStatus {
        DRAFT, PENDING_REVIEW, PUBLISHED, REJECTED, DELISTED
    }

    public boolean getCanPublish() {
        if (status == SkillStatus.DELISTED) return false;
        if (status == SkillStatus.PENDING_REVIEW) return false;
        if (lastPublishedAt == null) return true;
        if (updatedAt == null) return false;
        return updatedAt.isAfter(lastPublishedAt);
    }
}