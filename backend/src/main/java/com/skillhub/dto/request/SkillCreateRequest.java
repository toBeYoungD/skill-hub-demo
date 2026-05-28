package com.skillhub.dto.request;

import lombok.Data;
import com.skillhub.entity.Skill;

@Data
public class SkillCreateRequest {
    private String name;
    private String description;
    private String developer;
    private Long categoryId;
    private String version;
}