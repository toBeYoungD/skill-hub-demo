package com.skillhub.biz;

import com.skillhub.dto.SkillCreateRequest;
import com.skillhub.dto.SkillQueryRequest;
import com.skillhub.dto.SkillUpdateRequest;
import com.skillhub.domain.PublishRequest;
import com.skillhub.domain.Skill;
import com.skillhub.domain.SkillVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 技能核心业务接口。
 * Controller 只负责参数校验和响应包装，具体逻辑全部在此接口的实现中。
 */
public interface SkillBiz {

    /** 创建技能 */
    Skill create(SkillCreateRequest request, MultipartFile packageFile);

    /** 编辑技能 */
    Skill update(Long id, SkillUpdateRequest request, MultipartFile packageFile);

    /** 保存为草稿 */
    Skill saveAsDraft(Long id);

    /** 提交审批 */
    void submitReview(Long id, String changelog);

    /** 审批通过 */
    PublishRequest approveReview(Long requestId);

    /** 审批拒绝 */
    PublishRequest rejectReview(Long requestId, String reason);

    /** 待审批列表 */
    List<PublishRequest> pendingReviews(String status);

    /** 下架 */
    void delist(Long id, String reason);

    /** 恢复 */
    void restore(Long id);

    /** 查询列表 */
    Page<Skill> list(SkillQueryRequest request, Pageable pageable);

    /** 详情 */
    Skill detail(Long id);

    /** 删除（仅从未发布过的可物理删除） */
    void delete(Long id);

    /** 导出 */
    java.io.File export(Long id);

    /** 版本回滚 */
    SkillVersion rollback(Long skillId, Long versionId);

    /** 删除版本 */
    void deleteVersion(Long skillId, Long versionId);

    /** 某技能的审批历史 */
    List<PublishRequest> reviewHistory(Long skillId);

    /** 按开发者筛选技能 */
    Page<Skill> listByDeveloper(String developer, SkillQueryRequest request, Pageable pageable);

    /** 管理台统计 */
    Map<String, Object> adminStats();

    /** 批量审批 */
    List<Map<String, Object>> batchReview(String action, List<Long> ids, String reason);
}