package com.skillhub.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "skill")
@Data
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    private String packageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillStatus status = SkillStatus.DRAFT;

    private String developer;

    private Integer downloadCount = 0;

    private Integer useCount = 0;

    // 可见性
    @Column(length = 50)
    private String visibilityType = "PUBLIC";

    @Column(length = 2000)
    private String visibilityConfig;

    // 逻辑删除
    @Column(name = "is_delisted")
    private Boolean delisted = false;

    @Column(length = 500)
    private String delistedReason;

    private LocalDateTime delistedAt;

    @Column(length = 100)
    private String delistedBy;

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillVersion> versions = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastPublishedAt;

    public enum SkillStatus {
        DRAFT, PENDING_REVIEW, PUBLISHED, REJECTED, DELISTED
    }

    @Transient
    public boolean getCanPublish() {
        if (status == SkillStatus.DELISTED) return false;
        if (status == SkillStatus.PENDING_REVIEW) return false;
        if (lastPublishedAt == null) return true;
        if (updatedAt == null) return false;
        return updatedAt.isAfter(lastPublishedAt);
    }
}