package com.skillhub.controller;

import com.skillhub.dto.request.BatchCheckRequest;
import com.skillhub.dto.request.BatchCheckSkillItem;
import com.skillhub.entity.Skill;
import com.skillhub.entity.SkillChangeLog;
import com.skillhub.repository.SkillChangeLogRepository;
import com.skillhub.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final SkillRepository skillRepository;
    private final SkillChangeLogRepository changeLogRepository;

    @PostMapping("/batch-check")
    public ResponseEntity<Map<String, Object>> batchCheck(@RequestBody BatchCheckRequest request) {
        List<Map<String, Object>> delisted = new ArrayList<>();
        List<Map<String, Object>> upgradable = new ArrayList<>();

        for (BatchCheckSkillItem item : request.getSkills()) {
            Optional<Skill> optSkill = skillRepository.findByName(item.getSkillName());
            if (optSkill.isEmpty()) {
                Map<String, Object> d = new HashMap<>();
                d.put("skillName", item.getSkillName());
                d.put("reason", "技能不存在或已下架");
                delisted.add(d);
                continue;
            }

            Skill skill = optSkill.get();
            if (Boolean.TRUE.equals(skill.getDelisted())) {
                Map<String, Object> d = new HashMap<>();
                d.put("skillName", item.getSkillName());
                d.put("reason", skill.getDelistedReason() != null ? skill.getDelistedReason() : "已下架");
                delisted.add(d);
                continue;
            }

            if (skill.getStatus() == Skill.SkillStatus.PUBLISHED && !skill.getVersions().isEmpty()) {
                String latestVersion = skill.getVersions().stream()
                        .filter(v -> v.isLatest())
                        .map(v -> v.getVersion())
                        .findFirst()
                        .orElse(null);
                if (latestVersion != null && !latestVersion.equals(item.getVersion())) {
                    Map<String, Object> u = new HashMap<>();
                    u.put("skillName", item.getSkillName());
                    u.put("currentVersion", item.getVersion());
                    u.put("latestVersion", latestVersion);
                    upgradable.add(u);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("delisted", delisted);
        result.put("upgradable", upgradable);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/changelog")
    public ResponseEntity<Map<String, Object>> changelog(@RequestParam String since) {
        LocalDateTime sinceTime = LocalDateTime.parse(since);
        List<SkillChangeLog> changes = changeLogRepository.findByCreatedAtAfterOrderByCreatedAtAsc(sinceTime);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("changes", changes, "until", LocalDateTime.now().toString()));
        return ResponseEntity.ok(response);
    }
}