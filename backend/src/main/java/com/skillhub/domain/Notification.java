package com.skillhub.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Notification {

    private Long id;
    private String userId;
    private String event;
    private Long skillId;
    private String skillName;
    private String message;
    private Boolean read = false;
    private LocalDateTime createdAt;
}