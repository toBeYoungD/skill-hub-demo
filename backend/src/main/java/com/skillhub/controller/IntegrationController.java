package com.skillhub.controller;

import com.skillhub.biz.IntegrationBiz;
import com.skillhub.dto.BatchCheckRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationBiz integrationBiz;

    @PostMapping("/batch-check")
    public ResponseEntity<Map<String, Object>> batchCheck(@RequestBody BatchCheckRequest request) {
        return ResponseEntity.ok(Map.of("success", true, "data", integrationBiz.batchCheck(request)));
    }

    @GetMapping("/changelog")
    public ResponseEntity<Map<String, Object>> changelog(@RequestParam String since) {
        return ResponseEntity.ok(Map.of("success", true, "data", integrationBiz.changelog(LocalDateTime.parse(since))));
    }
}