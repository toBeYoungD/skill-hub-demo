package com.skillhub.dto.request;

import lombok.Data;

@Data
public class SkillUpdateRequest {
    private String name;
    private String description;
}