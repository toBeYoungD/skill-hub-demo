package com.skillhub.biz;

import com.skillhub.dao.SkillChangeLogMapper;
import com.skillhub.dao.SkillMapper;
import com.skillhub.dto.BatchCheckRequest;
import com.skillhub.domain.Skill;
import com.skillhub.domain.SkillChangeLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IntegrationBizImpl implements IntegrationBiz {

    private final SkillMapper skillMapper;
    private final SkillChangeLogMapper changeLogMapper;

    @Override
    public Map<String, Object> batchCheck(BatchCheckRequest request) {
        List<Map<String, Object>> delisted = new ArrayList<>();
        List<Map<String, Object>> upgradable = new ArrayList<>();

        for (var item : request.getSkills()) {
            Skill skill = skillMapper.selectByName(item.getSkillName());
            if (skill == null) {
                delisted.add(Map.of("skillName", item.getSkillName(), "reason", "技能不存在或已下架"));
                continue;
            }
            if (Boolean.TRUE.equals(skill.getDelisted())) {
                delisted.add(Map.of("skillName", item.getSkillName(), "reason",
                        skill.getDelistedReason() != null ? skill.getDelistedReason() : "已下架"));
                continue;
            }
            // 用 selectById 加载 version 列表
            Skill full = skillMapper.selectById(skill.getId());
            if (full.getStatus() == Skill.SkillStatus.PUBLISHED && full.getVersions() != null
                    && !full.getVersions().isEmpty()) {
                String latest = full.getVersions().stream().filter(v -> v.isLatest())
                        .map(v -> v.getVersion()).findFirst().orElse(null);
                if (latest != null && !latest.equals(item.getVersion())) {
                    upgradable.add(Map.of("skillName", item.getSkillName(),
                            "currentVersion", item.getVersion(), "latestVersion", latest));
                }
            }
        }
        return Map.of("delisted", delisted, "upgradable", upgradable);
    }

    @Override
    public Map<String, Object> changelog(LocalDateTime since) {
        List<SkillChangeLog> changes = changeLogMapper.selectByCreatedAtAfter(since);
        return Map.of("changes", changes, "until", LocalDateTime.now().toString());
    }
}