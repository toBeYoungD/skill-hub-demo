package com.skillhub.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "skill_version")
@Data
public class SkillVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(nullable = false)
    private String version;

    private String packageUrl;

    private String manifestUrl;

    @Column(length = 500)
    private String changelog;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status = VersionStatus.DRAFT;

    @Column(name = "is_latest")
    private boolean isLatest = false;

    @Column(name = "is_rollback")
    private boolean isRollback = false;

    @Column(name = "rolled_back_from")
    private String rolledBackFrom;

    @Column(length = 100)
    private String skillNameSnapshot;

    @Column(length = 1000)
    private String skillDescriptionSnapshot;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public enum VersionStatus {
        DRAFT, PUBLISHED
    }
}