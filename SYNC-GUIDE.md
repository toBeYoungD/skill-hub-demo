# 后台接口功能增补 — 同步指南

> 目标：在已改造的公司内部代码基础上，增量添加以下功能，不影响已有改造。

---

## 一、本次改动总览

| 新增接口 | 方法 | 说明 |
|---------|------|------|
| `GET /api/skills/my` | — | 按当前用户筛选"我的技能" |
| `GET /api/admin/stats` | — | 管理台统计数据 |
| `POST /api/admin/reviews/batch` | — | 批量审批 |
| `GET /api/admin/reviews?applicant=` | 改 | 按申请人筛选审批列表 |
| 审计日志 | 改 | approve/reject/restore 补写 SkillChangeLog |

---

## 二、改动文件与操作步骤

### 文件 1：`biz/SkillBiz.java`（接口）

**操作：在 `reviewHistory` 方法声明之后、文件末尾 `}` 之前，插入三个方法：**

```java
    /** 按开发者筛选技能 */
    Page<Skill> listByDeveloper(String developer, SkillQueryRequest request, Pageable pageable);

    /** 管理台统计 */
    Map<String, Object> adminStats();

    /** 批量审批 */
    List<Map<String, Object>> batchReview(String action, List<Long> ids, String reason);
```

**同时在文件头部 import 区加一行：**

```java
import java.util.Map;
```

---

### 文件 2：`biz/SkillBizImpl.java`（实现）

#### 2a. 补审计日志（三处）

**位置1 — `approveReview()` 中，`changeListener.onSkillUpgraded(...)` 之后、`log.info("审批通过"...)` 之前，插入：**

```java
        // 审计日志
        SkillChangeLog approveLog = new SkillChangeLog();
        approveLog.setSkillName(skill.getName());
        approveLog.setChangeType("APPROVED");
        approveLog.setDetails("{\"version\":\"" + newVersion + "\",\"reviewer\":\"" + permissionService.currentUserId() + "\"}");
        approveLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(approveLog);
```

> 注意：`permissionService` 在你的项目中可能是你司内部实现，如果 `currentUserId()` 返回的不是字符串，改为你司的用户 ID 获取方式。

**位置2 — `rejectReview()` 中，`notify(...)` 之后、`log.info("审批拒绝"...)` 之前，插入：**

```java
        // 审计日志
        SkillChangeLog rejectLog = new SkillChangeLog();
        rejectLog.setSkillName(skill.getName());
        rejectLog.setChangeType("REJECTED");
        rejectLog.setDetails("{\"reason\":\"" + reason + "\",\"reviewer\":\"" + permissionService.currentUserId() + "\"}");
        rejectLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(rejectLog);
```

**位置3 — `restore()` 中，`skillMapper.update(skill)` 之后、`log.info("恢复"...)` 之前，插入：**

```java
        // 审计日志
        SkillChangeLog restoreLog = new SkillChangeLog();
        restoreLog.setSkillName(skill.getName());
        restoreLog.setChangeType("RESTORED");
        restoreLog.setDetails("{}");
        restoreLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(restoreLog);
```

#### 2b. 新增三个方法

**在文件末尾、最后一个 `private` 方法之前插入以下全部代码：**

```java
    // ==================== 开发者筛选 ====================

    @Override
    public Page<Skill> listByDeveloper(String developer, SkillQueryRequest request, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        String name = request.getName() != null && !request.getName().isEmpty() ? request.getName() : null;
        String status = request.getStatus() != null ? request.getStatus().name() : null;

        List<Skill> all = skillMapper.selectAll().stream()
                .filter(s -> developer.equals(s.getDeveloper()))
                .collect(Collectors.toList());

        if (status != null)
            all = all.stream().filter(s -> s.getStatus().name().equals(status)).collect(Collectors.toList());
        if (name != null)
            all = all.stream().filter(s -> s.getName().contains(name)).collect(Collectors.toList());

        long total = all.size();
        int end = Math.min(offset + limit, all.size());
        List<Skill> page = offset < all.size() ? all.subList(offset, end) : List.of();
        return new PageImpl<>(page, pageable, total);
    }

    // ==================== 管理台统计 ====================

    @Override
    public Map<String, Object> adminStats() {
        List<Skill> allSkills = skillMapper.selectAll();
        List<PublishRequest> allReviews = publishRequestMapper.selectAll();
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSkills", allSkills.size());
        stats.put("publishedSkills", allSkills.stream()
                .filter(s -> s.getStatus() == Skill.SkillStatus.PUBLISHED).count());
        stats.put("pendingReviews", allReviews.stream()
                .filter(r -> r.getStatus() == PublishRequest.RequestStatus.PENDING).count());
        stats.put("approvedToday", allReviews.stream()
                .filter(r -> r.getStatus() == PublishRequest.RequestStatus.APPROVED
                        && r.getReviewedAt() != null && r.getReviewedAt().isAfter(today)).count());
        stats.put("rejectedToday", allReviews.stream()
                .filter(r -> r.getStatus() == PublishRequest.RequestStatus.REJECTED
                        && r.getReviewedAt() != null && r.getReviewedAt().isAfter(today)).count());
        stats.put("delistedTotal", allSkills.stream()
                .filter(s -> Boolean.TRUE.equals(s.getDelisted())).count());
        return stats;
    }

    // ==================== 批量审批 ====================

    @Override
    @Transactional
    public List<Map<String, Object>> batchReview(String action, List<Long> ids, String reason) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Long id : ids) {
            try {
                if ("approve".equals(action)) {
                    approveReview(id);
                } else {
                    rejectReview(id, reason);
                }
                results.add(Map.of("id", id, "success", true));
            } catch (Exception e) {
                results.add(Map.of("id", id, "success", false, "reason", e.getMessage()));
            }
        }
        return results;
    }
```

> `skillMapper.selectAll()` 和 `publishRequestMapper.selectAll()` 在你司的 MyBatis 实现中如果不存在，需要先在对应的 Mapper 接口和 XML 中添加。

> `adminStats()` 是全量查内存计算，数据量小时可用。大数据量时建议改为 SQL 聚合查询。

---

### 文件 3：`controller/SkillController.java`

**在 `published()` 方法之后插入新接口：**

```java
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> mySkills(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        String developer = request.getHeader("X-User-Id");
        SkillQueryRequest req = new SkillQueryRequest();
        req.setName(name);
        req.setStatus(status != null ? Skill.SkillStatus.valueOf(status) : null);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var result = skillBiz.listByDeveloper(developer, req, pageable);
        return ok(result.getContent(), Map.of("total", result.getTotalElements(), "page", page, "size", size));
    }
```

**需要新增的 import：**

```java
import jakarta.servlet.http.HttpServletRequest;
```

> `X-User-Id` 在这里是 Demo 写法。公司内部改为你们实际的用户 ID 获取方式（从 `request.getAttribute` 或 ThreadLocal 等）。

---

### 文件 4：`controller/ReviewController.java`

**完整替换此文件为：**

```java
package com.skillhub.controller;

import com.skillhub.biz.SkillBiz;
import com.skillhub.domain.PublishRequest;
import com.skillhub.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ReviewController {

    private final SkillBiz skillBiz;

    @GetMapping("/reviews")
    public ResponseEntity<Map<String, Object>> listReviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String applicant) {
        List<PublishRequest> all = skillBiz.pendingReviews(status);
        if (applicant != null) {
            all = all.stream().filter(r -> applicant.equals(r.getApplicant())).toList();
        }
        return ok(all);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of("success", true, "data", skillBiz.adminStats()));
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

    @PostMapping("/reviews/batch")
    public ResponseEntity<Map<String, Object>> batchReview(@RequestBody Map<String, Object> body) {
        String action = (String) body.get("action");
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("ids");
        List<Long> ids = idsRaw.stream().map(Long::valueOf).toList();
        String reason = (String) body.getOrDefault("reason", "");
        return ResponseEntity.ok(Map.of("success", true, "data", skillBiz.batchReview(action, ids, reason)));
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
```

> 与原版的区别：`listReviews` 新增 `applicant` 参数；新增 `stats()`；新增 `batchReview()`。

---

## 三、新增 API 说明

### `GET /api/skills/my?name=&status=&page=&size=`

按当前用户（从请求头或公司认证获取）筛选其提交的技能列表。

响应同 `GET /api/skills`。

### `GET /api/admin/stats`

```json
{
  "success": true,
  "data": {
    "totalSkills": 47,
    "publishedSkills": 30,
    "pendingReviews": 5,
    "approvedToday": 12,
    "rejectedToday": 3,
    "delistedTotal": 8
  }
}
```

### `POST /api/admin/reviews/batch`

```json
// 请求
{ "action": "reject", "ids": [1, 2, 3], "reason": "格式不合规" }

// 响应
{
  "success": true,
  "data": [
    { "id": 1, "success": true },
    { "id": 2, "success": true },
    { "id": 3, "success": false, "reason": "该申请不在待审批状态" }
  ]
}
```

### `GET /api/admin/reviews?status=PENDING&applicant=zhangsan`

在原有 `status` 参数基础上新增 `applicant` 可选参数，按申请人过滤。

---

## 四、注意事项

1. **所有改动都是增量**：不删除任何方法、不修改任何现有签名。可直接粘贴到公司内部代码。
2. **`selectAll()` 方法**：如果公司内部的 `SkillMapper` 和 `PublishRequestMapper` 没有 `selectAll()` 方法，需要先在接口和 XML 中添加——只是一个简单的 `SELECT * FROM skill`。
3. **权限系统**：`permissionService.currentUserId()` 和 `X-User-Id` header 在这里是 Demo 写法，你已在公司内部实现了真正的认证逻辑，直接使用你们的方式获取用户 ID 即可。
4. **批量审批**：通过/拒绝时内部逐条调用 `approveReview()`/`rejectReview()`，会触发各自的审计日志和通知，无需额外处理。
5. **数据量大时**：`adminStats()` 和 `listByDeveloper()` 都是全量查出后 Java 内存过滤，仅适用于 Demo/小数据量。生产环境建议改为 SQL 聚合查询。`SkillBizImpl` 中对应的 `skillMapper.selectAll()` 和 `publishRequestMapper.selectAll()` 如已改为分页查询，需要同步改这两个新方法。