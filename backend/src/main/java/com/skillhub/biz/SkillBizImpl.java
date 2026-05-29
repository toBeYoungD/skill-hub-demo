package com.skillhub.biz;

import com.skillhub.auth.PermissionService;
import com.skillhub.dto.request.SkillCreateRequest;
import com.skillhub.dto.request.SkillQueryRequest;
import com.skillhub.dto.request.SkillUpdateRequest;
import com.skillhub.entity.*;
import com.skillhub.integration.SkillChangeListener;
import com.skillhub.repository.*;
import com.skillhub.service.NotificationService;
import com.skillhub.service.SkillPackageValidator;
import com.skillhub.util.LocalStorageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillBizImpl implements SkillBiz {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final PublishRequestRepository publishRequestRepository;
    private final SkillChangeLogRepository changeLogRepository;
    private final LocalStorageUtil localStorageUtil;
    private final SkillPackageValidator skillPackageValidator;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final SkillChangeListener changeListener;

    // ==================== 创建 ====================

    @Override
    @Transactional
    public Skill create(SkillCreateRequest request, MultipartFile packageFile) {
        if (skillRepository.existsByName(request.getName())) {
            throw new BizException("技能名称已存在: " + request.getName());
        }

        Skill skill = new Skill();
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setDeveloper(request.getDeveloper());
        skill.setVisibilityType(request.getVisibilityType() != null ? request.getVisibilityType() : "PUBLIC");
        skill.setVisibilityConfig(request.getVisibilityConfig());
        skill.setStatus(Skill.SkillStatus.DRAFT);

        if (packageFile != null && !packageFile.isEmpty()) {
            skillPackageValidator.validate(packageFile);
            skill.setPackageUrl(upload(packageFile));
        }

        skill.setUpdatedAt(LocalDateTime.now());
        skill = skillRepository.save(skill);
        log.info("创建技能: id={}, name={}", skill.getId(), skill.getName());
        return skill;
    }

    // ==================== 编辑 ====================

    @Override
    @Transactional
    public Skill update(Long id, SkillUpdateRequest request, MultipartFile packageFile) {
        Skill skill = skillRepository.findById(id).orElseThrow(() -> new BizException("技能不存在"));

        if (request.getName() != null && !request.getName().isEmpty()) {
            if (!skill.getName().equals(request.getName()) && skillRepository.existsByName(request.getName())) {
                throw new BizException("技能名称已存在: " + request.getName());
            }
            skill.setName(request.getName());
        }
        if (request.getDescription() != null && !request.getDescription().isEmpty()) {
            skill.setDescription(request.getDescription());
        }

        if (packageFile != null && !packageFile.isEmpty()) {
            skillPackageValidator.validate(packageFile);
            if (skill.getPackageUrl() != null) localStorageUtil.deleteFile(skill.getPackageUrl());
            skill.setPackageUrl(upload(packageFile));
        }

        // 审批中编辑 → 自动作废旧申请
        if (skill.getStatus() == Skill.SkillStatus.PENDING_REVIEW) {
            cancelPending(skill.getId(), "技能内容已变更，旧申请自动作废");
            skill.setStatus(Skill.SkillStatus.DRAFT);
        }

        skill.setUpdatedAt(LocalDateTime.now());
        skillRepository.save(skill);
        log.info("更新技能: id={}", id);
        return skill;
    }

    // ==================== 保存为草稿 ====================

    @Override
    @Transactional
    public Skill saveAsDraft(Long id) {
        Skill skill = skillRepository.findById(id).orElseThrow(() -> new BizException("技能不存在"));
        Skill.SkillStatus oldStatus = skill.getStatus();

        // 如果正在审批中，保存回草稿时要作废旧审批
        if (oldStatus == Skill.SkillStatus.PENDING_REVIEW) {
            cancelPending(skill.getId(), "保存为草稿，审批申请自动作废");
        }

        skill.setStatus(Skill.SkillStatus.DRAFT);
        if (oldStatus != Skill.SkillStatus.DRAFT) {
            skill.setUpdatedAt(LocalDateTime.now());
        }
        skillRepository.save(skill);
        return skill;
    }

    // ==================== 审批流 ====================

    @Override
    @Transactional
    public void submitReview(Long skillId, String changelog) {
        Skill skill = skillRepository.findById(skillId).orElseThrow(() -> new BizException("技能不存在"));

        if (skill.getStatus() != Skill.SkillStatus.DRAFT
                && skill.getStatus() != Skill.SkillStatus.REJECTED
                && skill.getStatus() != Skill.SkillStatus.PUBLISHED) {
            throw new BizException("当前状态不允许提交审批: " + skill.getStatus());
        }

        cancelPending(skillId, "新的审批申请已提交，旧申请自动作废");

        PublishRequest req = new PublishRequest();
        req.setSkillId(skillId);
        req.setSkillName(skill.getName());
        req.setChangelog(changelog);
        req.setApplicant(permissionService.currentUserId());
        req.setSkillUpdatedAtSnapshot(skill.getUpdatedAt());
        publishRequestRepository.save(req);

        skill.setStatus(Skill.SkillStatus.PENDING_REVIEW);
        skillRepository.save(skill);
        log.info("提交审批: skillId={}", skillId);
    }

    @Override
    @Transactional
    public PublishRequest approveReview(Long requestId) {
        PublishRequest request = publishRequestRepository.findById(requestId)
                .orElseThrow(() -> new BizException("审批申请不存在"));
        if (request.getStatus() != PublishRequest.RequestStatus.PENDING) {
            throw new BizException("该申请不在待审批状态");
        }

        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new BizException("技能不存在"));

        // 并发校验
        if (request.getSkillUpdatedAtSnapshot() != null && skill.getUpdatedAt() != null
                && !skill.getUpdatedAt().equals(request.getSkillUpdatedAtSnapshot())) {
            request.setStatus(PublishRequest.RequestStatus.REJECTED);
            request.setRejectReason("技能内容已变更，请重新提交审批");
            request.setReviewer(permissionService.currentUserId());
            request.setReviewedAt(LocalDateTime.now());
            publishRequestRepository.save(request);
            skill.setStatus(Skill.SkillStatus.REJECTED);
            skillRepository.save(skill);
            notify(request.getApplicant(), "EXPIRED", skill.getId(), skill.getName(),
                    "技能「" + skill.getName() + "」的审批申请因内容变更已过期，请重新提交");
            return request;
        }

        // 版本号递增
        String latestVersion = null;
        if (!skill.getVersions().isEmpty()) {
            latestVersion = skill.getVersions().get(skill.getVersions().size() - 1).getVersion();
        }
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
        publishRequestRepository.save(request);

        notify(request.getApplicant(), "APPROVED", skill.getId(), skill.getName(),
                "技能「" + skill.getName() + "」已通过审批并发布");
        changeListener.onSkillUpgraded(skill.getName(), newVersion);
        log.info("审批通过: skillId={}", skill.getId());
        return request;
    }

    @Override
    @Transactional
    public PublishRequest rejectReview(Long requestId, String reason) {
        PublishRequest request = publishRequestRepository.findById(requestId)
                .orElseThrow(() -> new BizException("审批申请不存在"));
        if (request.getStatus() != PublishRequest.RequestStatus.PENDING) {
            throw new BizException("该申请不在待审批状态");
        }

        request.setStatus(PublishRequest.RequestStatus.REJECTED);
        request.setRejectReason(reason);
        request.setReviewer(permissionService.currentUserId());
        request.setReviewedAt(LocalDateTime.now());

        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new BizException("技能不存在"));
        skill.setStatus(skill.getLastPublishedAt() != null ? Skill.SkillStatus.PUBLISHED : Skill.SkillStatus.REJECTED);
        skillRepository.save(skill);
        publishRequestRepository.save(request);

        notify(request.getApplicant(), "REJECTED", skill.getId(), skill.getName(),
                "技能「" + skill.getName() + "」审批未通过，原因: " + reason);
        log.info("审批拒绝: skillId={}", skill.getId());
        return request;
    }

    @Override
    public List<PublishRequest> pendingReviews(String status) {
        if (status != null) {
            return publishRequestRepository.findAll().stream()
                    .filter(r -> r.getStatus().name().equals(status)).toList();
        }
        return publishRequestRepository.findAll();
    }

    // ==================== 下架 / 恢复 ====================

    @Override
    @Transactional
    public void delist(Long id, String reason) {
        Skill skill = skillRepository.findById(id).orElseThrow(() -> new BizException("技能不存在"));
        skill.setStatus(Skill.SkillStatus.DELISTED);
        skill.setDelisted(true);
        skill.setDelistedReason(reason);
        skill.setDelistedAt(LocalDateTime.now());
        skill.setDelistedBy(permissionService.currentUserId());
        skillRepository.save(skill);

        SkillChangeLog changeLog = new SkillChangeLog();
        changeLog.setSkillName(skill.getName());
        changeLog.setChangeType("DELISTED");
        changeLog.setDetails("{\"reason\":\"" + reason + "\"}");
        changeLogRepository.save(changeLog);

        changeListener.onSkillDelisted(skill.getName(), reason);
        notify(skill.getDeveloper(), "DELISTED", id, skill.getName(),
                "技能「" + skill.getName() + "」已被下架，原因: " + reason);
        log.info("下架: skillId={}", id);
    }

    @Override
    @Transactional
    public void restore(Long id) {
        Skill skill = skillRepository.findById(id).orElseThrow(() -> new BizException("技能不存在"));
        skill.setStatus(Skill.SkillStatus.PUBLISHED);
        skill.setDelisted(false);
        skill.setDelistedReason(null);
        skill.setDelistedAt(null);
        skill.setUpdatedAt(LocalDateTime.now());
        skillRepository.save(skill);
        log.info("恢复: skillId={}", id);
    }

    // ==================== 查询 ====================

    @Override
    public Page<Skill> list(SkillQueryRequest request, Pageable pageable) {
        if (request.getStatus() != null) {
            if (request.getName() != null && !request.getName().isEmpty()) {
                return skillRepository.findAll(
                        (root, query, cb) -> cb.and(
                                cb.equal(root.get("status"), request.getStatus()),
                                cb.like(root.get("name"), "%" + request.getName() + "%")), pageable);
            }
            return skillRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("status"), request.getStatus()), pageable);
        }
        if (request.getName() != null && !request.getName().isEmpty()) {
            return skillRepository.findAll(
                    (root, query, cb) -> cb.like(root.get("name"), "%" + request.getName() + "%"), pageable);
        }
        return skillRepository.findAll(pageable);
    }

    @Override
    public Skill detail(Long id) {
        return skillRepository.findById(id).orElseThrow(() -> new BizException("技能不存在"));
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public void delete(Long id) {
        Skill skill = skillRepository.findById(id).orElseThrow(() -> new BizException("技能不存在"));
        if (skill.getLastPublishedAt() != null) {
            throw new BizException("已发布过的技能不能物理删除，请使用下架功能");
        }
        if (skill.getStatus() != Skill.SkillStatus.DRAFT && skill.getStatus() != Skill.SkillStatus.REJECTED) {
            throw new BizException("只能删除草稿或未通过状态的技能");
        }
        if (skill.getPackageUrl() != null) localStorageUtil.deleteFile(skill.getPackageUrl());
        skill.getVersions().forEach(v -> {
            if (v.getPackageUrl() != null) localStorageUtil.deleteFile(v.getPackageUrl());
        });
        skillRepository.delete(skill);
    }

    // ==================== 版本管理 ====================

    @Override
    @Transactional
    public SkillVersion rollback(Long skillId, Long versionId) {
        Skill skill = skillRepository.findById(skillId).orElseThrow(() -> new BizException("技能不存在"));
        SkillVersion target = skill.getVersions().stream()
                .filter(v -> v.getId().equals(versionId)).findFirst()
                .orElseThrow(() -> new BizException("版本不存在"));

        String latestVersion = skill.getVersions().isEmpty() ? null
                : skill.getVersions().get(skill.getVersions().size() - 1).getVersion();
        String newVersion = String.valueOf(latestVersion == null ? 1 : Integer.parseInt(latestVersion) + 1);

        skill.getVersions().forEach(v -> v.setLatest(false));

        SkillVersion rollbackVersion = new SkillVersion();
        rollbackVersion.setSkill(skill);
        rollbackVersion.setVersion(newVersion);
        rollbackVersion.setPackageUrl(target.getPackageUrl());
        rollbackVersion.setChangelog("从版本 " + target.getVersion() + " 回滚");
        rollbackVersion.setStatus(SkillVersion.VersionStatus.PUBLISHED);
        rollbackVersion.setLatest(true);
        rollbackVersion.setRollback(true);
        rollbackVersion.setRolledBackFrom(target.getVersion());

        skill.getVersions().add(rollbackVersion);
        if (target.getSkillNameSnapshot() != null) skill.setName(target.getSkillNameSnapshot());
        if (target.getSkillDescriptionSnapshot() != null) skill.setDescription(target.getSkillDescriptionSnapshot());
        if (target.getPackageUrl() != null) skill.setPackageUrl(target.getPackageUrl());
        skill.setLastPublishedAt(LocalDateTime.now());
        skillRepository.save(skill);
        return rollbackVersion;
    }

    @Override
    @Transactional
    public void deleteVersion(Long skillId, Long versionId) {
        Skill skill = skillRepository.findById(skillId).orElseThrow(() -> new BizException("技能不存在"));
        SkillVersion version = skill.getVersions().stream()
                .filter(v -> v.getId().equals(versionId)).findFirst()
                .orElseThrow(() -> new BizException("版本不存在"));

        if (version.isLatest() && version.getStatus() == SkillVersion.VersionStatus.PUBLISHED) {
            throw new BizException("不能删除最新的已发布版本");
        }
        if (version.getPackageUrl() != null) localStorageUtil.deleteFile(version.getPackageUrl());
        skill.getVersions().remove(version);
        if (!skill.getVersions().isEmpty()) {
            skill.getVersions().get(skill.getVersions().size() - 1).setLatest(true);
        }
        skillRepository.save(skill);
    }

    @Override
    public List<PublishRequest> reviewHistory(Long skillId) {
        return publishRequestRepository.findBySkillIdOrderByCreatedAtDesc(skillId);
    }

    // ==================== 导出 ====================

    @Override
    public java.io.File export(Long skillId) {
        Skill skill = detail(skillId);
        try {
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"), "skill-" + skillId);
            if (tempDir.exists()) deleteDir(tempDir);
            tempDir.mkdirs();

            String latestVer = skill.getVersions().isEmpty() ? "1"
                    : skill.getVersions().get(skill.getVersions().size() - 1).getVersion();

            StringBuilder md = new StringBuilder();
            md.append("---\nname: ").append(sanitize(skill.getName()))
              .append("\ndescription: ").append(skill.getDescription() != null ? skill.getDescription() : "")
              .append("\nversion: ").append(latestVer).append("\n---\n\n");
            md.append("# ").append(skill.getName()).append("\n\n");
            if (skill.getDescription() != null) md.append(skill.getDescription()).append("\n\n");
            md.append("## 基本信息\n\n- 开发者: ").append(skill.getDeveloper())
              .append("\n- 状态: ").append(skill.getStatus())
              .append("\n- 下载: ").append(skill.getDownloadCount())
              .append(" | 使用: ").append(skill.getUseCount()).append("\n\n## 版本历史\n\n");
            skill.getVersions().forEach(v -> {
                md.append("- v").append(v.getVersion()).append(" (").append(v.getStatus()).append(")");
                if (v.isRollback()) md.append(" [回滚]");
                if (v.getChangelog() != null) md.append(": ").append(v.getChangelog());
                md.append("\n");
            });
            java.nio.file.Files.writeString(new java.io.File(tempDir, "SKILL.md").toPath(), md.toString());

            java.io.File scriptsDir = new java.io.File(tempDir, "scripts");
            scriptsDir.mkdirs();
            copyFile(skill.getPackageUrl(), new java.io.File(scriptsDir, "package"));

            java.io.File zipFile = new java.io.File(tempDir.getParent(), "skill-" + skillId + ".zip");
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    new java.io.FileOutputStream(zipFile))) {
                zipDir(tempDir, tempDir, zos);
            }
            deleteDir(tempDir);
            return zipFile;
        } catch (Exception e) {
            throw new BizException("导出失败: " + e.getMessage());
        }
    }

    // ==================== 内部工具方法 ====================

    private String upload(MultipartFile file) {
        try { return localStorageUtil.uploadPackage(file); }
        catch (Exception e) { throw new BizException("文件上传失败: " + e.getMessage()); }
    }

    private void cancelPending(Long skillId, String reason) {
        publishRequestRepository.findBySkillIdOrderByCreatedAtDesc(skillId).stream()
                .filter(r -> r.getStatus() == PublishRequest.RequestStatus.PENDING)
                .forEach(r -> {
                    r.setStatus(PublishRequest.RequestStatus.REJECTED);
                    r.setRejectReason(reason);
                    r.setReviewedAt(LocalDateTime.now());
                    publishRequestRepository.save(r);
                });
    }

    private void notify(String userId, String event, Long skillId, String skillName, String message) {
        notificationService.notify(userId, event, skillId, skillName, message);
    }

    private void copyFile(String relativePath, java.io.File dest) {
        if (relativePath == null) return;
        try (java.io.InputStream in = localStorageUtil.getFileInputStream(relativePath)) {
            java.nio.file.Files.copy(in, dest.toPath());
        } catch (Exception e) { log.warn("复制文件失败: {}", relativePath, e); }
    }

    private void zipDir(java.io.File root, java.io.File dir, java.util.zip.ZipOutputStream zos) throws Exception {
        for (java.io.File f : dir.listFiles()) {
            String entryName = root.toURI().relativize(f.toURI()).getPath();
            if (f.isDirectory()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(entryName + "/"));
                zos.closeEntry();
                zipDir(root, f, zos);
            } else {
                zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                java.nio.file.Files.copy(f.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    private void deleteDir(java.io.File dir) {
        if (dir.isDirectory()) for (java.io.File f : dir.listFiles()) deleteDir(f);
        dir.delete();
    }

    private String sanitize(String name) {
        if (name == null) return "unnamed";
        return name.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fff-]", "-").replaceAll("-+", "-");
    }
}