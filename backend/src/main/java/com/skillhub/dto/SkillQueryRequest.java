package com.skillhub.dto;

import lombok.Data;
import com.skillhub.domain.Skill;

@Data
public class SkillQueryRequest {
    private String name;
    private Skill.SkillStatus status;
}