package com.skillhub.controller;

import com.skillhub.biz.SkillBiz;
import com.skillhub.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ReviewController {

    private final SkillBiz skillBiz;

    @GetMapping("/reviews")
    public ResponseEntity<Map<String, Object>> listReviews(@RequestParam(required = false) String status) {
        return ok(skillBiz.pendingReviews(status));
    }

    @PostMapping("/reviews/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id) {
        skillBiz.approveReview(id);
        return okMsg("审批通过");
    }

    @PostMapping("/reviews/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody RejectReviewRequest body) {
        skillBiz.rejectReview(id, body.getReason());
        return okMsg("审批已拒绝");
    }

    @PostMapping("/skills/{id}/delist")
    public ResponseEntity<Map<String, Object>> delist(@PathVariable Long id, @RequestBody DelistRequest body) {
        skillBiz.delist(id, body.getReason());
        return okMsg("技能已下架");
    }

    @PostMapping("/skills/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Long id) {
        skillBiz.restore(id);
        return okMsg("技能已恢复");
    }

    private static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
    private static ResponseEntity<Map<String, Object>> okMsg(String msg) {
        return ResponseEntity.ok(Map.of("success", true, "message", msg));
    }
}