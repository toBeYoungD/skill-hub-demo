package com.skillhub.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PublishRequest {

    private Long id;
    private Long skillId;
    private String skillName;
    private String changelog;
    private RequestStatus status = RequestStatus.PENDING;
    private String applicant;
    private String reviewer;
    private String rejectReason;
    private LocalDateTime skillUpdatedAtSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED
    }
}