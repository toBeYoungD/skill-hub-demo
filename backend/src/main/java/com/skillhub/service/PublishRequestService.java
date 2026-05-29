package com.skillhub.service;

import com.skillhub.auth.PermissionService;
import com.skillhub.entity.PublishRequest;
import com.skillhub.entity.Skill;
import com.skillhub.entity.SkillVersion;
import com.skillhub.integration.SkillChangeListener;
import com.skillhub.repository.PublishRequestRepository;
import com.skillhub.repository.SkillChangeLogRepository;
import com.skillhub.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublishRequestService {

    private final PublishRequestRepository requestRepository;
    private final SkillRepository skillRepository;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final SkillChangeListener changeListener;
    private final SkillChangeLogRepository changeLogRepository;

    @Transactional
    public PublishRequest submitReview(Long skillId, String changelog) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        if (skill.getStatus() != Skill.SkillStatus.DRAFT
                && skill.getStatus() != Skill.SkillStatus.REJECTED
                && skill.getStatus() != Skill.SkillStatus.PUBLISHED) {
            throw new RuntimeException("当前状态不允许提交审批: " + skill.getStatus());
        }

        // 作废该 skill 之前的待审批申请
        cancelPendingRequests(skillId, "新的审批申请已提交，旧申请自动作废");

        PublishRequest request = new PublishRequest();
        request.setSkillId(skillId);
        request.setSkillName(skill.getName());
        request.setChangelog(changelog);
        request.setApplicant(permissionService.currentUserId());
        request.setSkillUpdatedAtSnapshot(skill.getUpdatedAt());
        request = requestRepository.save(request);

        skill.setStatus(Skill.SkillStatus.PENDING_REVIEW);
        skillRepository.save(skill);

        log.info("提交审批: skillId={}, requestId={}", skillId, request.getId());
        return request;
    }

    @Transactional
    public PublishRequest approve(Long requestId) {
        PublishRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("审批申请不存在"));

        if (request.getStatus() != PublishRequest.RequestStatus.PENDING)
            throw new RuntimeException("该申请不在待审批状态");

        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        // 并发校验
        if (request.getSkillUpdatedAtSnapshot() != null && skill.getUpdatedAt() != null
                && !skill.getUpdatedAt().equals(request.getSkillUpdatedAtSnapshot())) {
            request.setStatus(PublishRequest.RequestStatus.REJECTED);
            request.setRejectReason("技能内容已变更，请重新提交审批");
            request.setReviewer(permissionService.currentUserId());
            request.setReviewedAt(LocalDateTime.now());
            requestRepository.save(request);

            skill.setStatus(Skill.SkillStatus.REJECTED);
            skillRepository.save(skill);

            notificationService.notify(request.getApplicant(), "EXPIRED", skill.getId(),
                    skill.getName(), "技能「" + skill.getName() + "」的审批申请因内容变更已过期，请重新提交");
            return request;
        }

        // 执行发布
        String latestVersion = null;
        if (!skill.getVersions().isEmpty())
            latestVersion = skill.getVersions().get(skill.getVersions().size() - 1).getVersion();
        String newVersion = String.valueOf(latestVersion == null ? 1 : Integer.parseInt(latestVersion) + 1);

        skill.getVersions().forEach(v -> v.setLatest(false));

        SkillVersion newVersionEntity = new SkillVersion();
        newVersionEntity.setSkill(skill);
        newVersionEntity.setVersion(newVersion);
        newVersionEntity.setPackageUrl(skill.getPackageUrl());
        newVersionEntity.setChangelog(request.getChangelog());
        newVersionEntity.setStatus(SkillVersion.VersionStatus.PUBLISHED);
        newVersionEntity.setLatest(true);
        newVersionEntity.setSkillNameSnapshot(skill.getName());
        newVersionEntity.setSkillDescriptionSnapshot(skill.getDescription());
        skill.getVersions().add(newVersionEntity);

        skill.setStatus(Skill.SkillStatus.PUBLISHED);
        skill.setLastPublishedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        skillRepository.save(skill);

        request.setStatus(PublishRequest.RequestStatus.APPROVED);
        request.setReviewer(permissionService.currentUserId());
        request.setReviewedAt(LocalDateTime.now());
        requestRepository.save(request);

        notificationService.notify(request.getApplicant(), "APPROVED", skill.getId(),
                skill.getName(), "技能「" + skill.getName() + "」已通过审批并发布");
        changeListener.onSkillUpgraded(skill.getName(), newVersion);

        log.info("审批通过: skillId={}, requestId={}, version={}", skill.getId(), requestId, newVersion);
        return request;
    }

    @Transactional
    public PublishRequest reject(Long requestId, String reason) {
        PublishRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("审批申请不存在"));

        if (request.getStatus() != PublishRequest.RequestStatus.PENDING)
            throw new RuntimeException("该申请不在待审批状态");

        request.setStatus(PublishRequest.RequestStatus.REJECTED);
        request.setRejectReason(reason);
        request.setReviewer(permissionService.currentUserId());
        request.setReviewedAt(LocalDateTime.now());

        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        // 拒绝后回到之前的状态
        if (skill.getLastPublishedAt() != null)
            skill.setStatus(Skill.SkillStatus.PUBLISHED);
        else
            skill.setStatus(Skill.SkillStatus.REJECTED);

        skillRepository.save(skill);
        requestRepository.save(request);

        notificationService.notify(request.getApplicant(), "REJECTED", skill.getId(),
                skill.getName(), "技能「" + skill.getName() + "」审批未通过，原因: " + reason);

        log.info("审批拒绝: skillId={}, requestId={}, reason={}", skill.getId(), requestId, reason);
        return request;
    }

    @Transactional
    public void cancelPendingRequests(Long skillId, String reason) {
        requestRepository.findBySkillIdOrderByCreatedAtDesc(skillId).stream()
                .filter(r -> r.getStatus() == PublishRequest.RequestStatus.PENDING)
                .forEach(r -> {
                    r.setStatus(PublishRequest.RequestStatus.REJECTED);
                    r.setRejectReason(reason);
                    r.setReviewedAt(LocalDateTime.now());
                    requestRepository.save(r);
                });
    }

    public List<PublishRequest> getReviewHistory(Long skillId) {
        return requestRepository.findBySkillIdOrderByCreatedAtDesc(skillId);
    }

    public List<PublishRequest> getPendingReviews() {
        return requestRepository.findByStatusOrderByCreatedAtDesc(PublishRequest.RequestStatus.PENDING);
    }

    public List<PublishRequest> getAllReviews() {
        return requestRepository.findAll();
    }
}