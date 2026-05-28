package com.skillhub.entity;

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

    private String iconUrl;

    private String packageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillStatus status = SkillStatus.DRAFT;

    private Long categoryId;

    private String developer;

    private Integer downloadCount = 0;

    private Integer useCount = 0;

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillVersion> versions = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastPublishedAt;

    public enum SkillStatus {
        DRAFT, PUBLISHED, ARCHIVED
    }

    @Transient
    public boolean getCanPublish() {
        if (status == SkillStatus.ARCHIVED) return false;
        if (lastPublishedAt == null) return true;
        if (updatedAt == null) return false;
        return updatedAt.isAfter(lastPublishedAt);
    }
}