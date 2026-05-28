package com.skillhub.controller;

import com.skillhub.dto.request.SkillCreateRequest;
import com.skillhub.dto.request.SkillQueryRequest;
import com.skillhub.dto.request.SkillUpdateRequest;
import com.skillhub.entity.Skill;
import com.skillhub.service.SkillService;
import com.skillhub.util.VersionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@Slf4j
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSkill(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("developer") String developer,
            @RequestParam(value = "version", required = false, defaultValue = "1") String version,
            @RequestParam(value = "categoryId") String categoryIdStr,
            @RequestParam(value = "iconFile", required = false) MultipartFile iconFile,
            @RequestParam(value = "packageFile", required = false) MultipartFile packageFile,
            @RequestParam(value = "manifestFile", required = false) MultipartFile manifestFile) {

        try {
            SkillCreateRequest request = new SkillCreateRequest();
            request.setName(name);
            request.setDescription(description);
            request.setDeveloper(developer);
            request.setVersion(version);
            request.setCategoryId(parseLong(categoryIdStr));

            Skill skill = skillService.createSkill(request, iconFile, packageFile, manifestFile);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "技能创建成功");
            response.put("data", skill);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建技能失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "创建技能失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable Long id) {
        try {
            Skill skill = skillService.getSkillDetail(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", skill);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取技能失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取技能失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSkills(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String categoryIdStr,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            SkillQueryRequest request = new SkillQueryRequest();
            request.setName(name);
            request.setCategoryId(parseLong(categoryIdStr));
            request.setStatus(status != null ? Skill.SkillStatus.valueOf(status) : null);

            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Skill> skills = skillService.getSkills(request, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", skills.getContent());
            response.put("total", skills.getTotalElements());
            response.put("page", page);
            response.put("size", size);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询技能列表失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "查询技能列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/published")
    public ResponseEntity<Map<String, Object>> getPublishedSkills() {
        try {
            var skills = skillService.getPublishedSkills();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", skills);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取已发布技能失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取已发布技能失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> updateSkillJson(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            SkillUpdateRequest request = new SkillUpdateRequest();
            request.setName(body.get("name"));
            request.setDescription(body.get("description"));
            request.setCategoryId(parseLong(body.get("categoryId")));

            Skill skill = skillService.updateSkill(id, request, null, null);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "技能更新成功");
            response.put("data", skill);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新技能失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "更新技能失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> updateSkill(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(value = "categoryId", required = false) String categoryIdStr,
            @RequestParam(value = "iconFile", required = false) MultipartFile iconFile,
            @RequestParam(value = "packageFile", required = false) MultipartFile packageFile) {

        try {
            SkillUpdateRequest request = new SkillUpdateRequest();
            request.setName(name);
            request.setDescription(description);
            request.setCategoryId(parseLong(categoryIdStr));

            Skill skill = skillService.updateSkill(id, request, iconFile, packageFile);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "技能更新成功");
            response.put("data", skill);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新技能失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "更新技能失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Map<String, Object>> publishSkill(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "PATCH") String versionType,
            @RequestParam(required = false) String changelog) {
        try {
            VersionUtil.VersionType type = VersionUtil.VersionType.valueOf(versionType.toUpperCase());
            skillService.publishSkill(id, changelog, type);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "技能发布成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("发布技能失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "发布技能失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSkill(@PathVariable Long id) {
        try {
            skillService.deleteSkill(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "技能删除成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除技能失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除技能失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/{id}/download")
    public ResponseEntity<Map<String, Object>> downloadSkill(@PathVariable Long id) {
        try {
            skillService.incrementDownloadCount(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "下载记录成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("下载记录失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "下载记录失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/{id}/use")
    public ResponseEntity<Map<String, Object>> useSkill(@PathVariable Long id) {
        try {
            skillService.incrementUseCount(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "使用记录成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("使用记录失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "使用记录失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isEmpty() || "undefined".equals(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<Resource> exportSkill(@PathVariable Long id) {
        java.io.File zip = skillService.exportSkill(id);
        Resource resource = new FileSystemResource(zip);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=skill-" + id + ".zip")
                .header("Content-Type", "application/zip")
                .body(resource);
    }
}