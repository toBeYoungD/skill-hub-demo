package com.skillhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Skill Hub 核心流程集成测试。
 * 运行: gradlew test --tests "com.skillhub.SkillHubIntegrationTest"
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SkillHubIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static Long skillId;
    private static Long rejectedRequestId;

    // =================== 1. 创建技能 ===================
    @Test @Order(1)
    @DisplayName("创建技能 → DRAFT, canPublish=true")
    void createSkill() throws Exception {
        var result = mockMvc.perform(multipart("/api/skills")
                        .param("name", "自动化测试技能")
                        .param("description", "用于集成测试")
                        .param("developer", "测试员"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.canPublish").value(true))
                .andReturn();
        skillId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
        System.out.println("[PASS] 创建技能 id=" + skillId + ", status=DRAFT");
    }

    // =================== 2. 重名校验 ===================
    @Test @Order(2)
    @DisplayName("重名创建 → 400")
    void duplicateName() throws Exception {
        mockMvc.perform(multipart("/api/skills")
                        .param("name", "自动化测试技能")
                        .param("description", "重复")
                        .param("developer", "测试员"))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] 重名创建被拒绝");
    }

    // =================== 3. 编辑技能 ===================
    @Test @Order(3)
    @DisplayName("编辑 → canPublish=true")
    void updateSkill() throws Exception {
        mockMvc.perform(put("/api/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"自动化测试技能V2\",\"description\":\"更新后的描述\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("自动化测试技能V2"))
                .andExpect(jsonPath("$.data.canPublish").value(true));
        System.out.println("[PASS] 编辑成功, canPublish=true");
    }

    // =================== 4. 保存为草稿 ===================
    @Test @Order(4)
    @DisplayName("保存 → DRAFT")
    void saveSkill() throws Exception {
        mockMvc.perform(post("/api/skills/" + skillId + "/save"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        System.out.println("[PASS] 保存为草稿");
    }

    // =================== 5. 提交审批 ===================
    @Test @Order(5)
    @DisplayName("提交审批 → PENDING_REVIEW, canPublish=false")
    void submitReview() throws Exception {
        mockMvc.perform(multipart("/api/skills/" + skillId + "/publish")
                        .param("changelog", "首次发布"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.canPublish").value(false));
        System.out.println("[PASS] 提交审批, status=PENDING_REVIEW");
    }

    // =================== 6. 审批中不能重复提交 ===================
    @Test @Order(6)
    @DisplayName("审批中重复提交 → 400")
    void duplicateSubmit() throws Exception {
        mockMvc.perform(multipart("/api/skills/" + skillId + "/publish")
                        .param("changelog", "重复"))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] 审批中重复提交被拒绝");
    }

    // =================== 7. 审批拒绝 ===================
    @Test @Order(7)
    @DisplayName("审批拒绝 → 技能恢复可发布状态 + 通知")
    void rejectReview() throws Exception {
        var listResult = mockMvc.perform(get("/api/admin/reviews?status=PENDING")).andReturn();
        rejectedRequestId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("data").get(0).get("id").asLong();

        mockMvc.perform(post("/api/admin/reviews/" + rejectedRequestId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"缺少必要声明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.canPublish").value(true));

        mockMvc.perform(get("/api/notifications").header("X-User-Id", "demo-user"))
                .andExpect(jsonPath("$.data[0].event").value("REJECTED"));
        System.out.println("[PASS] 审批拒绝, 技能可重新提交, 有REJECTED通知");
    }

    // =================== 8. 重新提交 + 审批通过 ===================
    @Test @Order(8)
    @DisplayName("重新提交 → 通过 → PUBLISHED + v1")
    void resubmitAndApprove() throws Exception {
        mockMvc.perform(multipart("/api/skills/" + skillId + "/publish")
                        .param("changelog", "已修复"))
                .andExpect(status().isOk());

        var listResult = mockMvc.perform(get("/api/admin/reviews?status=PENDING")).andReturn();
        Long requestId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("data").get(0).get("id").asLong();

        mockMvc.perform(post("/api/admin/reviews/" + requestId + "/approve"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.versions.length()").value(1))
                .andExpect(jsonPath("$.data.versions[0].version").value("1"))
                .andExpect(jsonPath("$.data.canPublish").value(false));

        mockMvc.perform(get("/api/notifications").header("X-User-Id", "demo-user"))
                .andExpect(jsonPath("$.data[0].event").value("APPROVED"));
        System.out.println("[PASS] 审批通过, status=PUBLISHED, v1, 有APPROVED通知");
    }

    // =================== 9. 未编辑不能再次发布 ===================
    @Test @Order(9)
    @DisplayName("未编辑 canPublish=false")
    void cantPublishWithoutEdit() throws Exception {
        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.canPublish").value(false));
        System.out.println("[PASS] 未编辑不能发布");
    }

    // =================== 10. 审批历史 ===================
    @Test @Order(10)
    @DisplayName("审批历史包含2条以上记录")
    void reviewHistory() throws Exception {
        mockMvc.perform(get("/api/skills/" + skillId + "/review-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        System.out.println("[PASS] 审批历史有记录");
    }

    // =================== 11. 下架 ===================
    @Test @Order(11)
    @DisplayName("下架 → DELISTED")
    void delist() throws Exception {
        mockMvc.perform(post("/api/admin/skills/" + skillId + "/delist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"版本过旧\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.status").value("DELISTED"))
                .andExpect(jsonPath("$.data.delisted").value(true));
        System.out.println("[PASS] 下架成功");
    }

    // =================== 12. 恢复 ===================
    @Test @Order(12)
    @DisplayName("恢复 → PUBLISHED")
    void restore() throws Exception {
        mockMvc.perform(post("/api/admin/skills/" + skillId + "/restore"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.delisted").value(false));
        System.out.println("[PASS] 恢复成功");
    }

    // =================== 13. batch-check ===================
    @Test @Order(13)
    @DisplayName("batch-check 检测升级和下架")
    void batchCheck() throws Exception {
        mockMvc.perform(post("/api/integration/batch-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[" +
                                "{\"skillName\":\"自动化测试技能V2\",\"version\":\"0\"}," +
                                "{\"skillName\":\"不存在的技能\",\"version\":\"1\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upgradable[0].latestVersion").value("1"))
                .andExpect(jsonPath("$.data.delisted[0].skillName").value("不存在的技能"));
        System.out.println("[PASS] batch-check 正确");
    }

    // =================== 14. 导出 ===================
    @Test @Order(14)
    @DisplayName("导出 → 200 OK")
    void exportSkill() throws Exception {
        mockMvc.perform(get("/api/skills/" + skillId + "/export"))
                .andExpect(status().isOk());
        System.out.println("[PASS] 导出成功");
    }

    // =================== 15. 已发布过的技能不能物理删除 ===================
    @Test @Order(15)
    @DisplayName("已发布过的技能 → 拒绝物理删除")
    void cantDeletePublished() throws Exception {
        mockMvc.perform(delete("/api/skills/" + skillId))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] 已发布过的技能拒绝物理删除");
    }

    // =================== 16. 从未发布的技能可物理删除 ===================
    @Test @Order(16)
    @DisplayName("从未发布的技能 → 可物理删除")
    void deleteNeverPublished() throws Exception {
        var result = mockMvc.perform(multipart("/api/skills")
                        .param("name", "待删除技能")
                        .param("description", "测试删除")
                        .param("developer", "测试员"))
                .andExpect(status().isOk()).andReturn();
        Long newId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/skills/" + newId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/skills/" + newId)).andExpect(status().is4xxClientError());
        System.out.println("[PASS] 从未发布的技能可物理删除");
    }

    // =================== 17. 并发：审批中编辑 → 旧审批过期 ===================
    @Test @Order(17)
    @DisplayName("审批中编辑 → 旧审批自动过期")
    void editWhilePending() throws Exception {
        // 重新提交审批
        mockMvc.perform(multipart("/api/skills/" + skillId + "/publish")
                        .param("changelog", "v3发布"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        // 编辑（触发旧审批作废）
        mockMvc.perform(put("/api/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"自动化测试技能V4\"}"))
                .andExpect(status().isOk());

        // 状态应回到 DRAFT
        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        System.out.println("[PASS] 审批中编辑 → 旧审批作废, 回到DRAFT");
    }

    // =================== 18. BUG修复：审批中保存 → 旧审批作废 ===================
    @Test @Order(18)
    @DisplayName("审批中点击保存 → 旧审批作废，管理员无法通过")
    void saveWhilePending() throws Exception {
        mockMvc.perform(multipart("/api/skills/" + skillId + "/publish")
                        .param("changelog", "v5发布"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        var listResult = mockMvc.perform(get("/api/admin/reviews?status=PENDING")).andReturn();
        Long requestId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("data").get(0).get("id").asLong();

        mockMvc.perform(post("/api/skills/" + skillId + "/save"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/skills/" + skillId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/admin/reviews/" + requestId + "/approve"))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] 审批中保存 → 旧审批作废, 管理员无法通过");
    }
}