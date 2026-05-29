package com.skillhub.controller;

import com.skillhub.biz.BizException;
import com.skillhub.biz.SkillBiz;
import com.skillhub.entity.Skill;
import com.skillhub.entity.SkillVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/skills/{skillId}/versions")
@RequiredArgsConstructor
public class SkillVersionController {

    private final SkillBiz skillBiz;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@PathVariable Long skillId) {
        Skill skill = skillBiz.detail(skillId);
        var versions = skill.getVersions().stream()
                .sorted((a, b) -> Integer.compare(Integer.parseInt(b.getVersion()), Integer.parseInt(a.getVersion())))
                .toList();
        return ok(versions, Map.of("skillName", skill.getName(), "total", versions.size()));
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long skillId, @PathVariable Long versionId) {
        Skill skill = skillBiz.detail(skillId);
        SkillVersion version = skill.getVersions().stream()
                .filter(v -> v.getId().equals(versionId)).findFirst()
                .orElseThrow(() -> new BizException("版本不存在"));
        return ok(version, Map.of("skillName", skill.getName()));
    }

    @PostMapping("/{versionId}/rollback")
    public ResponseEntity<Map<String, Object>> rollback(@PathVariable Long skillId, @PathVariable Long versionId) {
        return ok(skillBiz.rollback(skillId, versionId));
    }

    @DeleteMapping("/{versionId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long skillId, @PathVariable Long versionId) {
        skillBiz.deleteVersion(skillId, versionId);
        return okMsg("版本已删除");
    }

    private static ResponseEntity<Map<String, Object>> ok(Object data) {
        try { return ResponseEntity.ok(Map.of("success", true, "data", data)); }
        catch (Exception e) { return bad(e); }
    }

    private static ResponseEntity<Map<String, Object>> ok(Object data, Map<String, Object> extra) {
        var map = new java.util.HashMap<>(extra);
        map.put("success", true);
        map.put("data", data);
        return ResponseEntity.ok(map);
    }

    private static ResponseEntity<Map<String, Object>> okMsg(String msg) {
        return ResponseEntity.ok(Map.of("success", true, "message", msg));
    }

    private static ResponseEntity<Map<String, Object>> bad(Exception e) {
        String msg = (e instanceof BizException) ? e.getMessage() : "操作失败: " + e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", msg));
    }
}