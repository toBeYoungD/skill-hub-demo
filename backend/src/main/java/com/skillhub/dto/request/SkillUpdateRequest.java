package com.skillhub.dto.request;

import lombok.Data;
import com.skillhub.entity.Skill;

@Data
public class SkillUpdateRequest {
    private String name;
    private String description;
    private Long categoryId;
}