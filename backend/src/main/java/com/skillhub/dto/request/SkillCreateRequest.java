package com.skillhub.dto.request;

import lombok.Data;

@Data
public class SkillCreateRequest {
    private String name;
    private String description;
    private String developer;
    private String version;
    private String visibilityType;
    private String visibilityConfig;
}