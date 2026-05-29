package com.skillhub.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchCheckRequest {
    private List<BatchCheckSkillItem> skills;
}