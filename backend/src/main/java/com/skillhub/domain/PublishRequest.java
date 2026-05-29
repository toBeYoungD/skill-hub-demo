package com.skillhub.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "publish_request")
@Data
public class PublishRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long skillId;

    @Column(length = 100)
    private String skillName;

    @Column(length = 500)
    private String changelog;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(length = 100)
    private String applicant;

    @Column(length = 100)
    private String reviewer;

    @Column(length = 500)
    private String rejectReason;

    private LocalDateTime skillUpdatedAtSnapshot;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED
    }
}