package com.skillhub.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class BatchCheckRequest {
    private List<BatchCheckSkillItem> skills;
}