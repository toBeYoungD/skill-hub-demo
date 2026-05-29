package com.skillhub.dto.request;

import lombok.Data;

@Data
public class BatchCheckSkillItem {
    private String skillName;
    private String version;
}