package com.skillhub.controller;

import com.skillhub.dto.request.DelistRequest;
import com.skillhub.dto.request.RejectReviewRequest;
import com.skillhub.entity.PublishRequest;
import com.skillhub.entity.Skill;
import com.skillhub.entity.SkillChangeLog;
import com.skillhub.integration.SkillChangeListener;
import com.skillhub.repository.SkillChangeLogRepository;
import com.skillhub.repository.SkillRepository;
import com.skillhub.service.PublishRequestService;
import com.skillhub.service.NotificationService;
import com.skillhub.auth.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final PublishRequestService publishRequestService;
    private final SkillRepository skillRepository;
    private final SkillChangeListener changeListener;
    private final SkillChangeLogRepository changeLogRepository;
    private final NotificationService notificationService;
    private final PermissionService permissionService;

    @GetMapping("/reviews")
    public ResponseEntity<Map<String, Object>> listReviews(@RequestParam(required = false) String status) {
        List<PublishRequest> reviews = status != null
                ? publishRequestService.getAllReviews().stream()
                    .filter(r -> r.getStatus().name().equals(status)).toList()
                : publishRequestService.getAllReviews();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", reviews);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reviews/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id) {
        try {
            PublishRequest req = publishRequestService.approve(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "审批通过");
            response.put("data", req);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/reviews/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody RejectReviewRequest body) {
        try {
            PublishRequest req = publishRequestService.reject(id, body.getReason());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "审批已拒绝");
            response.put("data", req);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/skills/{id}/delist")
    public ResponseEntity<Map<String, Object>> delist(@PathVariable Long id, @RequestBody DelistRequest body) {
        try {
            Skill skill = skillRepository.findById(id).orElseThrow(() -> new RuntimeException("技能不存在"));
            skill.setStatus(Skill.SkillStatus.DELISTED);
            skill.setDelisted(true);
            skill.setDelistedReason(body.getReason());
            skill.setDelistedAt(LocalDateTime.now());
            skill.setDelistedBy(permissionService.currentUserId());
            skill.setUpdatedAt(LocalDateTime.now());
            skillRepository.save(skill);

            SkillChangeLog log = new SkillChangeLog();
            log.setSkillName(skill.getName());
            log.setChangeType("DELISTED");
            log.setDetails("{\"reason\":\"" + body.getReason() + "\"}");
            changeLogRepository.save(log);

            changeListener.onSkillDelisted(skill.getName(), body.getReason());
            notificationService.notify(skill.getDeveloper(), "DELISTED", id, skill.getName(),
                    "技能「" + skill.getName() + "」已被下架，原因: " + body.getReason());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "技能已下架");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/skills/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Long id) {
        try {
            Skill skill = skillRepository.findById(id).orElseThrow(() -> new RuntimeException("技能不存在"));
            skill.setStatus(Skill.SkillStatus.PUBLISHED);
            skill.setDelisted(false);
            skill.setDelistedReason(null);
            skill.setDelistedAt(null);
            skill.setUpdatedAt(LocalDateTime.now());
            skillRepository.save(skill);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "技能已恢复");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}