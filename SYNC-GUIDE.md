# 后台接口功能增补 — 同步指南

> 本文档供公司内部 AI 智能体读取，实现增量代码合并。

---

## 新增功能总览

| # | 接口 | 说明 |
|---|------|------|
| 1 | `GET /api/skills/my` | 按当前用户筛选"我的技能" |
| 2 | `GET /api/admin/stats` | 管理台统计数据 |
| 3 | `POST /api/admin/reviews/batch` | 批量审批 |
| 4 | `GET /api/admin/reviews?applicant=` | 审批列表按申请人筛选 |
| 5 | 审计日志 | approve / reject / restore 补写 SkillChangeLog |

**原则：** 所有改动均为增量，不删除任何现有方法、不修改任何现有签名。

---

## 三、改动步骤

### 步骤 1：`biz/SkillBiz.java` — 接口加方法声明

**定位：** 在现有 `reviewHistory` 声明和文件结尾 `}` 之间，已有以下三个方法声明。如果你的版本中看不到，请新增。

```java
    /** 某技能的审批历史 */
    List<PublishRequest> reviewHistory(Long skillId);

    /** 按开发者筛选技能 */                          // ← 此处开始是新增
    Page<Skill> listByDeveloper(String developer, SkillQueryRequest request, Pageable pageable);

    /** 管理台统计 */
    Map<String, Object> adminStats();

    /** 批量审批 */
    List<Map<String, Object>> batchReview(String action, List<Long> ids, String reason);
}                                                    // ← 文件末尾
```

另外确认文件头部 import 区有 `import java.util.Map;`，若无则加。

---

### 步骤 2：`biz/SkillBizImpl.java` — 补审计日志（三处精确插入）

#### 插入点 1：`approveReview()` 方法内

定位代码片段：

```java
        notify(request.getApplicant(), "APPROVED", skill.getId(), skill.getName(),
                "技能「" + skill.getName() + "」已通过审批并发布");
        changeListener.onSkillUpgraded(skill.getName(), newVersion);

        log.info("审批通过: skillId={}", skill.getId());   // ← 在这一行之前插入
```

在 `log.info` **之前**插入：

```java
        // 审计日志
        SkillChangeLog approveLog = new SkillChangeLog();
        approveLog.setSkillName(skill.getName());
        approveLog.setChangeType("APPROVED");
        approveLog.setDetails("{\"version\":\"" + newVersion + "\",\"reviewer\":\""
                + permissionService.currentUserId() + "\"}");
        approveLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(approveLog);
```

---

#### 插入点 2：`rejectReview()` 方法内

定位代码片段：

```java
        notify(request.getApplicant(), "REJECTED", skill.getId(), skill.getName(),
                "技能「" + skill.getName() + "」审批未通过，原因: " + reason);

        log.info("审批拒绝: skillId={}", skill.getId());   // ← 在这一行之前插入
```

在 `log.info` **之前**插入：

```java
        // 审计日志
        SkillChangeLog rejectLog = new SkillChangeLog();
        rejectLog.setSkillName(skill.getName());
        rejectLog.setChangeType("REJECTED");
        rejectLog.setDetails("{\"reason\":\"" + reason + "\",\"reviewer\":\""
                + permissionService.currentUserId() + "\"}");
        rejectLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(rejectLog);
```

---

#### 插入点 3：`restore()` 方法内

定位代码片段：

```java
        skillMapper.update(skill);
        log.info("恢复: skillId={}", id);      // ← 在这一行之前插入
    }
```

在 `log.info` **之前**插入：

```java
        // 审计日志
        SkillChangeLog restoreLog = new SkillChangeLog();
        restoreLog.setSkillName(skill.getName());
        restoreLog.setChangeType("RESTORED");
        restoreLog.setDetails("{}");
        restoreLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(restoreLog);
```

---

### 步骤 3：`biz/SkillBizImpl.java` — 末尾加三个新方法

在 `SkillBizImpl` 类的最后一个 `private` 方法（`deleteDir` 或 `sanitize`）**之前**插入：

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

---

### 步骤 4：`controller/SkillController.java` — 加 `/my` 端点

**定位：** 在 `published()` 方法之后，`updateJson()` 方法之前，插入新端点。你的文件应已修改过——确认存在以下结构：

```java
    @GetMapping("/published")
    public ResponseEntity<Map<String, Object>> published() {
        return ok(skillBiz.list(new SkillQueryRequest(), Pageable.unpaged()).getContent());
    }

    // ★ 在 published() 和 updateJson() 之间插入以下代码

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

    @PutMapping(value = "/{id}", consumes = "application/json")    // ← 这个方法是已有的，不要动
```

**文件头部确认有 import：**

```java
import jakarta.servlet.http.HttpServletRequest;
```

---

### 步骤 5：`controller/ReviewController.java` — 完整替换

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

> 相比原版增加了：`applicant` 参数、`/stats`、`/reviews/batch`。

---

## 四、新增 API 参考

### `GET /api/skills/my?name=&status=&page=&size=`

按当前用户筛选其提交的技能。响应同 `GET /api/skills`。

### `GET /api/admin/stats`

```json
{ "totalSkills": 47, "publishedSkills": 30, "pendingReviews": 5,
  "approvedToday": 12, "rejectedToday": 3, "delistedTotal": 8 }
```

### `POST /api/admin/reviews/batch`

请求：`{ "action": "reject", "ids": [1,2,3], "reason": "格式不合规" }`

响应：`[ { "id": 1, "success": true }, { "id": 3, "success": false, "reason": "..." } ]`

---

## 五、注意事项

1. `skillMapper.selectAll()` 和 `publishRequestMapper.selectAll()` — 如果团队内部的 Mapper 还没加这个方法，在对应接口加声明并在 XML 中加一个 `SELECT * FROM xxx`。
2. `listByDeveloper` 和 `adminStats` 是全量查后内存筛选，仅适合小数据量。生产环境建议改为 SQL 聚合查询。
3. `X-User-Id` 和 `permissionService.currentUserId()` 是 Demo 写法，请替换为团队内的真实认证获取方式。