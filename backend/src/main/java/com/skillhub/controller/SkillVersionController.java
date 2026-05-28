package com.skillhub.controller;

import com.skillhub.entity.Skill;
import com.skillhub.entity.SkillVersion;
import com.skillhub.service.SkillService;
import com.skillhub.util.VersionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills/{skillId}/versions")
@RequiredArgsConstructor
@Slf4j
public class SkillVersionController {

    private final SkillService skillService;
    private final VersionUtil versionUtil;

    /**
     * 获取技能的所有版本
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSkillVersions(@PathVariable Long skillId) {
        try {
            Skill skill = skillService.getSkillDetail(skillId);

            // 按版本号降序排列（最新的在前）
            List<SkillVersion> versions = skill.getVersions();
            versions.sort((v1, v2) -> {
                // 已发布的版本排前面，然后按版本号排序
                int statusCompare = v2.getStatus().compareTo(v1.getStatus());
                if (statusCompare != 0) {
                    return statusCompare;
                }
                return versionUtil.compareVersions(v2.getVersion(), v1.getVersion());
            });

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", versions);
            response.put("skillName", skill.getName());
            response.put("total", versions.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取技能版本列表失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取技能版本列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取指定版本详情
     */
    @GetMapping("/{versionId}")
    public ResponseEntity<Map<String, Object>> getVersionDetail(
            @PathVariable Long skillId,
            @PathVariable Long versionId) {
        try {
            Skill skill = skillService.getSkillDetail(skillId);

            SkillVersion version = skill.getVersions().stream()
                    .filter(v -> v.getId().equals(versionId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("版本不存在"));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", version);
            response.put("skillName", skill.getName());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取版本详情失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取版本详情失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 回滚到指定版本
     */
    @PostMapping("/{versionId}/rollback")
    public ResponseEntity<Map<String, Object>> rollbackToVersion(
            @PathVariable Long skillId,
            @PathVariable Long versionId) {
        try {
            Skill skill = skillService.getSkillDetail(skillId);

            SkillVersion targetVersion = skill.getVersions().stream()
                    .filter(v -> v.getId().equals(versionId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("版本不存在"));

            if (targetVersion.getStatus() != SkillVersion.VersionStatus.PUBLISHED) {
                throw new RuntimeException("只能回滚到已发布的版本");
            }

            SkillVersion newVersion = skillService.rollbackSkillVersion(skill, targetVersion);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("成功回滚到版本 %s，创建新版本 %s",
                    targetVersion.getVersion(), newVersion.getVersion()));
            response.put("data", Map.of(
                    "skill", skill,
                    "newVersion", newVersion
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("版本回滚失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "版本回滚失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除版本
     */
    @DeleteMapping("/{versionId}")
    public ResponseEntity<Map<String, Object>> deleteVersion(
            @PathVariable Long skillId,
            @PathVariable Long versionId) {
        try {
            Skill skill = skillService.getSkillDetail(skillId);

            SkillVersion version = skill.getVersions().stream()
                    .filter(v -> v.getId().equals(versionId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("版本不存在"));

            // 不允许删除最新已发布版本
            if (version.isLatest() && version.getStatus() == SkillVersion.VersionStatus.PUBLISHED) {
                throw new RuntimeException("不能删除最新的已发布版本");
            }

            skillService.deleteSkillVersion(skill, version);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "版本删除成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除版本失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除版本失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}