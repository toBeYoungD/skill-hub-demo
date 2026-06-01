package com.skillhub.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SkillVersion {

    private Long id;
    private Long skillId;

    @JsonIgnore
    private Skill skill;

    private String version;
    private String packageUrl;
    private String manifestUrl;
    private String changelog;
    private VersionStatus status = VersionStatus.DRAFT;
    private boolean latest = false;
    private boolean rollback = false;
    private String rolledBackFrom;

    // 注意: 以下三个命名对应MyBatis映射的 skill_name_snapshot / skill_description_snapshot
    // 但Java命名保持驼峰。MyBatis mapUnderscoreToCamelCase 和 XML 可覆盖
    private String skillNameSnapshot;
    private String skillDescriptionSnapshot;

    private LocalDateTime createdAt;

    public enum VersionStatus {
        DRAFT, PUBLISHED
    }
}