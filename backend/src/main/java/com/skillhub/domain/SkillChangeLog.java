package com.skillhub.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SkillChangeLog {

    private Long id;
    private String skillName;
    private String changeType;
    private String details;
    private LocalDateTime createdAt;
}