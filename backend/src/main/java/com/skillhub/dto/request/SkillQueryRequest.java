package com.skillhub.dto.request;

import lombok.Data;
import com.skillhub.entity.Skill;

@Data
public class SkillQueryRequest {
    private String name;
    private Skill.SkillStatus status;
}