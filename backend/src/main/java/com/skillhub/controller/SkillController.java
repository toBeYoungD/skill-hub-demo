package com.skillhub.controller;

import com.skillhub.biz.SkillBiz;
import com.skillhub.dto.request.*;
import com.skillhub.entity.Skill;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
public class SkillController {

    private final SkillBiz skillBiz;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("developer") String developer,
            @RequestParam(value = "packageFile", required = false) MultipartFile packageFile,
            @RequestParam(value = "visibilityType", required = false, defaultValue = "PUBLIC") String visibilityType,
            @RequestParam(value = "visibilityConfig", required = false) String visibilityConfig) {
        SkillCreateRequest req = new SkillCreateRequest();
        req.setName(name); req.setDescription(description); req.setDeveloper(developer);
        req.setVisibilityType(visibilityType); req.setVisibilityConfig(visibilityConfig);
        return ok(skillBiz.create(req, packageFile));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        SkillQueryRequest req = new SkillQueryRequest();
        req.setName(name);
        req.setStatus(status != null ? Skill.SkillStatus.valueOf(status) : null);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var result = skillBiz.list(req, pageable);
        return ok(result.getContent(), Map.of("total", result.getTotalElements(), "page", page, "size", size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return ok(skillBiz.detail(id));
    }

    @GetMapping("/published")
    public ResponseEntity<Map<String, Object>> published() {
        return ok(skillBiz.list(new SkillQueryRequest(), Pageable.unpaged()).getContent());
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> updateJson(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SkillUpdateRequest req = new SkillUpdateRequest();
        req.setName(body.get("name")); req.setDescription(body.get("description"));
        return ok(skillBiz.update(id, req, null));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> updateFile(@PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(value = "packageFile", required = false) MultipartFile packageFile) {
        SkillUpdateRequest req = new SkillUpdateRequest();
        req.setName(name); req.setDescription(description);
        return ok(skillBiz.update(id, req, packageFile));
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Object>> save(@PathVariable Long id) {
        return ok(skillBiz.saveAsDraft(id));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Map<String, Object>> publish(@PathVariable Long id,
            @RequestParam(required = false) String changelog) {
        skillBiz.submitReview(id, changelog);
        return okMsg("已提交审批");
    }

    @GetMapping("/{id}/review-history")
    public ResponseEntity<Map<String, Object>> reviewHistory(@PathVariable Long id) {
        return ok(skillBiz.reviewHistory(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        skillBiz.delete(id);
        return okMsg("技能已删除");
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<Resource> export(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=skill-" + id + ".zip")
                .header("Content-Type", "application/zip")
                .body(new FileSystemResource(skillBiz.export(id)));
    }

    private static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
    private static ResponseEntity<Map<String, Object>> ok(Object data, Map<String, Object> extra) {
        var map = new HashMap<>(extra);
        map.put("success", true); map.put("data", data);
        return ResponseEntity.ok(map);
    }
    private static ResponseEntity<Map<String, Object>> okMsg(String msg) {
        return ResponseEntity.ok(Map.of("success", true, "message", msg));
    }
}