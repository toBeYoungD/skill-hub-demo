package com.skillhub.biz;

import com.skillhub.dto.BatchCheckRequest;
import com.skillhub.domain.Skill;
import com.skillhub.domain.SkillChangeLog;
import com.skillhub.repository.SkillChangeLogRepository;
import com.skillhub.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IntegrationBizImpl implements IntegrationBiz {

    private final SkillRepository skillRepository;
    private final SkillChangeLogRepository changeLogRepository;

    @Override
    public Map<String, Object> batchCheck(BatchCheckRequest request) {
        List<Map<String, Object>> delisted = new ArrayList<>();
        List<Map<String, Object>> upgradable = new ArrayList<>();

        for (var item : request.getSkills()) {
            Optional<Skill> opt = skillRepository.findByName(item.getSkillName());
            if (opt.isEmpty()) {
                delisted.add(Map.of("skillName", item.getSkillName(), "reason", "技能不存在或已下架"));
                continue;
            }
            Skill skill = opt.get();
            if (Boolean.TRUE.equals(skill.getDelisted())) {
                delisted.add(Map.of("skillName", item.getSkillName(), "reason",
                        skill.getDelistedReason() != null ? skill.getDelistedReason() : "已下架"));
                continue;
            }
            if (skill.getStatus() == Skill.SkillStatus.PUBLISHED && !skill.getVersions().isEmpty()) {
                String latest = skill.getVersions().stream().filter(v -> v.isLatest())
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
        List<SkillChangeLog> changes = changeLogRepository.findByCreatedAtAfterOrderByCreatedAtAsc(since);
        return Map.of("changes", changes, "until", LocalDateTime.now().toString());
    }
}