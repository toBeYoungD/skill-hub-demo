package com.skillhub.biz;

import com.skillhub.dao.*;
import com.skillhub.dto.SkillCreateRequest;
import com.skillhub.dto.SkillQueryRequest;
import com.skillhub.dto.SkillUpdateRequest;
import com.skillhub.domain.*;
import com.skillhub.integration.SkillChangeListener;
import com.skillhub.security.PermissionService;
import com.skillhub.service.NotificationService;
import com.skillhub.service.SkillPackageValidator;
import com.skillhub.util.LocalStorageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillBizImpl implements SkillBiz {

    private final SkillMapper skillMapper;
    private final SkillVersionMapper versionMapper;
    private final PublishRequestMapper publishRequestMapper;
    private final SkillChangeLogMapper changeLogMapper;
    private final LocalStorageUtil localStorageUtil;
    private final SkillPackageValidator skillPackageValidator;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final SkillChangeListener changeListener;

    // ==================== 创建 ====================

    @Override
    @Transactional
    public Skill create(SkillCreateRequest request, MultipartFile packageFile) {
        if (skillMapper.existsByName(request.getName())) {
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
        skillMapper.insert(skill);
        log.info("创建技能: id={}, name={}", skill.getId(), skill.getName());
        return skill;
    }

    // ==================== 编辑 ====================

    @Override
    @Transactional
    public Skill update(Long id, SkillUpdateRequest request, MultipartFile packageFile) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) throw new BizException("技能不存在");

        if (request.getName() != null && !request.getName().isEmpty()) {
            if (!skill.getName().equals(request.getName())
                    && skillMapper.countByNameExcludingId(request.getName(), id) > 0) {
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

        if (skill.getStatus() == Skill.SkillStatus.PENDING_REVIEW) {
            cancelPending(skill.getId(), "技能内容已变更，旧申请自动作废");
            skill.setStatus(Skill.SkillStatus.DRAFT);
        }

        skill.setUpdatedAt(LocalDateTime.now());
        skillMapper.update(skill);

        // 重新加载 versions
        skill.setVersions(versionMapper.selectBySkillId(id));

        log.info("更新技能: id={}", id);
        return skill;
    }

    // ==================== 保存为草稿 ====================

    @Override
    @Transactional
    public Skill saveAsDraft(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) throw new BizException("技能不存在");
        Skill.SkillStatus oldStatus = skill.getStatus();

        if (oldStatus == Skill.SkillStatus.PENDING_REVIEW) {
            cancelPending(skill.getId(), "保存为草稿，审批申请自动作废");
        }

        skill.setStatus(Skill.SkillStatus.DRAFT);
        if (oldStatus != Skill.SkillStatus.DRAFT) {
            skill.setUpdatedAt(LocalDateTime.now());
        }
        skillMapper.update(skill);
        skill.setVersions(versionMapper.selectBySkillId(id));
        return skill;
    }

    // ==================== 审批流 ====================

    @Override
    @Transactional
    public void submitReview(Long skillId, String changelog) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) throw new BizException("技能不存在");

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
        req.setCreatedAt(LocalDateTime.now());
        publishRequestMapper.insert(req);

        skill.setStatus(Skill.SkillStatus.PENDING_REVIEW);
        skillMapper.update(skill);
        log.info("提交审批: skillId={}", skillId);
    }

    @Override
    @Transactional
    public PublishRequest approveReview(Long requestId) {
        PublishRequest request = publishRequestMapper.selectById(requestId);
        if (request == null) throw new BizException("审批申请不存在");
        if (request.getStatus() != PublishRequest.RequestStatus.PENDING) {
            throw new BizException("该申请不在待审批状态");
        }

        Skill skill = skillMapper.selectById(request.getSkillId());
        if (skill == null) throw new BizException("技能不存在");
        List<SkillVersion> versions = versionMapper.selectBySkillId(skill.getId());
        skill.setVersions(versions);

        // 并发校验
        if (request.getSkillUpdatedAtSnapshot() != null && skill.getUpdatedAt() != null
                && !skill.getUpdatedAt().equals(request.getSkillUpdatedAtSnapshot())) {
            request.setStatus(PublishRequest.RequestStatus.REJECTED);
            request.setRejectReason("技能内容已变更，请重新提交审批");
            request.setReviewer(permissionService.currentUserId());
            request.setReviewedAt(LocalDateTime.now());
            publishRequestMapper.update(request);
            skill.setStatus(Skill.SkillStatus.REJECTED);
            skillMapper.update(skill);
            notify(request.getApplicant(), "EXPIRED", skill.getId(), skill.getName(),
                    "技能「" + skill.getName() + "」的审批申请因内容变更已过期，请重新提交");
            return request;
        }

        // 版本号递增
        String latestVersion = versions.isEmpty() ? null
                : versions.get(versions.size() - 1).getVersion();
        String newVersion = String.valueOf(latestVersion == null ? 1 : Integer.parseInt(latestVersion) + 1);

        versionMapper.unsetLatest(skill.getId());

        SkillVersion newVersionEntity = new SkillVersion();
        newVersionEntity.setSkillId(skill.getId());
        newVersionEntity.setVersion(newVersion);
        newVersionEntity.setPackageUrl(skill.getPackageUrl());
        newVersionEntity.setChangelog(request.getChangelog());
        newVersionEntity.setStatus(SkillVersion.VersionStatus.PUBLISHED);
        newVersionEntity.setLatest(true);
        newVersionEntity.setSkillNameSnapshot(skill.getName());
        newVersionEntity.setSkillDescriptionSnapshot(skill.getDescription());
        newVersionEntity.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(newVersionEntity);

        skill.setStatus(Skill.SkillStatus.PUBLISHED);
        skill.setLastPublishedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        skillMapper.update(skill);

        request.setStatus(PublishRequest.RequestStatus.APPROVED);
        request.setReviewer(permissionService.currentUserId());
        request.setReviewedAt(LocalDateTime.now());
        publishRequestMapper.update(request);

        notify(request.getApplicant(), "APPROVED", skill.getId(), skill.getName(),
                "技能「" + skill.getName() + "」已通过审批并发布");
        changeListener.onSkillUpgraded(skill.getName(), newVersion);

        // 审计日志
        SkillChangeLog approveLog = new SkillChangeLog();
        approveLog.setSkillName(skill.getName());
        approveLog.setChangeType("APPROVED");
        approveLog.setDetails("{\"version\":\"" + newVersion + "\",\"reviewer\":\"" + permissionService.currentUserId() + "\"}");
        approveLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(approveLog);

        log.info("审批通过: skillId={}", skill.getId());
        return request;
    }

    @Override
    @Transactional
    public PublishRequest rejectReview(Long requestId, String reason) {
        PublishRequest request = publishRequestMapper.selectById(requestId);
        if (request == null) throw new BizException("审批申请不存在");
        if (request.getStatus() != PublishRequest.RequestStatus.PENDING) {
            throw new BizException("该申请不在待审批状态");
        }

        request.setStatus(PublishRequest.RequestStatus.REJECTED);
        request.setRejectReason(reason);
        request.setReviewer(permissionService.currentUserId());
        request.setReviewedAt(LocalDateTime.now());

        Skill skill = skillMapper.selectById(request.getSkillId());
        if (skill == null) throw new BizException("技能不存在");
        skill.setStatus(skill.getLastPublishedAt() != null ? Skill.SkillStatus.PUBLISHED : Skill.SkillStatus.REJECTED);
        skillMapper.update(skill);
        publishRequestMapper.update(request);

        notify(request.getApplicant(), "REJECTED", skill.getId(), skill.getName(),
                "技能「" + skill.getName() + "」审批未通过，原因: " + reason);

        // 审计日志
        SkillChangeLog rejectLog = new SkillChangeLog();
        rejectLog.setSkillName(skill.getName());
        rejectLog.setChangeType("REJECTED");
        rejectLog.setDetails("{\"reason\":\"" + reason + "\",\"reviewer\":\"" + permissionService.currentUserId() + "\"}");
        rejectLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(rejectLog);

        log.info("审批拒绝: skillId={}", skill.getId());
        return request;
    }

    @Override
    public List<PublishRequest> pendingReviews(String status) {
        if (status != null) {
            return publishRequestMapper.selectByStatus(status);
        }
        return publishRequestMapper.selectAll();
    }

    // ==================== 下架 / 恢复 ====================

    @Override
    @Transactional
    public void delist(Long id, String reason) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) throw new BizException("技能不存在");
        skill.setStatus(Skill.SkillStatus.DELISTED);
        skill.setDelisted(true);
        skill.setDelistedReason(reason);
        skill.setDelistedAt(LocalDateTime.now());
        skill.setDelistedBy(permissionService.currentUserId());
        skillMapper.update(skill);

        SkillChangeLog changeLog = new SkillChangeLog();
        changeLog.setSkillName(skill.getName());
        changeLog.setChangeType("DELISTED");
        changeLog.setDetails("{\"reason\":\"" + reason + "\"}");
        changeLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(changeLog);

        changeListener.onSkillDelisted(skill.getName(), reason);
        notify(skill.getDeveloper(), "DELISTED", id, skill.getName(),
                "技能「" + skill.getName() + "」已被下架，原因: " + reason);
        log.info("下架: skillId={}", id);
    }

    @Override
    @Transactional
    public void restore(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) throw new BizException("技能不存在");
        skill.setStatus(Skill.SkillStatus.PUBLISHED);
        skill.setDelisted(false);
        skill.setDelistedReason(null);
        skill.setDelistedAt(null);
        skill.setUpdatedAt(LocalDateTime.now());
        skillMapper.update(skill);

        // 审计日志
        SkillChangeLog restoreLog = new SkillChangeLog();
        restoreLog.setSkillName(skill.getName());
        restoreLog.setChangeType("RESTORED");
        restoreLog.setDetails("{}");
        restoreLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(restoreLog);

        log.info("恢复: skillId={}", id);
    }

    // ==================== 查询 ====================

    @Override
    public Page<Skill> list(SkillQueryRequest request, Pageable pageable) {
        List<Skill> results;
        long total;

        String name = request.getName() != null && !request.getName().isEmpty() ? request.getName() : null;
        String status = request.getStatus() != null ? request.getStatus().name() : null;
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        if (status != null && name != null) {
            results = skillMapper.selectByStatusAndName(status, name, offset, limit);
            total = skillMapper.countByStatusAndName(status, name);
        } else if (status != null) {
            results = skillMapper.selectByStatusOnly(status, offset, limit);
            total = skillMapper.countByStatus(status);
        } else if (name != null) {
            results = skillMapper.selectByNameOnly(name, offset, limit);
            total = skillMapper.countByName(name);
        } else {
            results = skillMapper.selectPage(offset, limit);
            total = skillMapper.countAll();
        }

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public Skill detail(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) throw new BizException("技能不存在");
        return skill;
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public void delete(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) throw new BizException("技能不存在");
        if (skill.getLastPublishedAt() != null) {
            throw new BizException("已发布过的技能不能物理删除，请使用下架功能");
        }
        if (skill.getStatus() != Skill.SkillStatus.DRAFT && skill.getStatus() != Skill.SkillStatus.REJECTED) {
            throw new BizException("只能删除草稿或未通过状态的技能");
        }

        // 删除关联版本
        List<SkillVersion> versions = versionMapper.selectBySkillId(id);
        for (SkillVersion v : versions) {
            if (v.getPackageUrl() != null) localStorageUtil.deleteFile(v.getPackageUrl());
            versionMapper.deleteById(v.getId());
        }

        if (skill.getPackageUrl() != null) localStorageUtil.deleteFile(skill.getPackageUrl());
        skillMapper.deleteById(id);
    }

    // ==================== 版本管理 ====================

    @Override
    @Transactional
    public SkillVersion rollback(Long skillId, Long versionId) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) throw new BizException("技能不存在");
        List<SkillVersion> versions = versionMapper.selectBySkillId(skillId);
        skill.setVersions(versions);

        SkillVersion target = versions.stream().filter(v -> v.getId().equals(versionId)).findFirst()
                .orElseThrow(() -> new BizException("版本不存在"));

        String latestVersion = versions.isEmpty() ? null
                : versions.get(versions.size() - 1).getVersion();
        String newVersion = String.valueOf(latestVersion == null ? 1 : Integer.parseInt(latestVersion) + 1);

        versionMapper.unsetLatest(skillId);

        SkillVersion rollbackVersion = new SkillVersion();
        rollbackVersion.setSkillId(skillId);
        rollbackVersion.setVersion(newVersion);
        rollbackVersion.setPackageUrl(target.getPackageUrl());
        rollbackVersion.setChangelog("从版本 " + target.getVersion() + " 回滚");
        rollbackVersion.setStatus(SkillVersion.VersionStatus.PUBLISHED);
        rollbackVersion.setLatest(true);
        rollbackVersion.setRollback(true);
        rollbackVersion.setRolledBackFrom(target.getVersion());
        rollbackVersion.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(rollbackVersion);

        if (target.getSkillNameSnapshot() != null) skill.setName(target.getSkillNameSnapshot());
        if (target.getSkillDescriptionSnapshot() != null) skill.setDescription(target.getSkillDescriptionSnapshot());
        if (target.getPackageUrl() != null) skill.setPackageUrl(target.getPackageUrl());
        skill.setLastPublishedAt(LocalDateTime.now());
        skillMapper.update(skill);
        return rollbackVersion;
    }

    @Override
    @Transactional
    public void deleteVersion(Long skillId, Long versionId) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) throw new BizException("技能不存在");
        List<SkillVersion> versions = versionMapper.selectBySkillId(skillId);

        SkillVersion version = versions.stream().filter(v -> v.getId().equals(versionId)).findFirst()
                .orElseThrow(() -> new BizException("版本不存在"));

        if (version.isLatest() && version.getStatus() == SkillVersion.VersionStatus.PUBLISHED) {
            throw new BizException("不能删除最新的已发布版本");
        }
        if (version.getPackageUrl() != null) localStorageUtil.deleteFile(version.getPackageUrl());
        versionMapper.deleteById(versionId);

        // 重设 latest
        versions.remove(version);
        if (!versions.isEmpty()) {
            SkillVersion last = versions.get(versions.size() - 1);
            last.setLatest(true);
            versionMapper.update(last);
        }
    }

    @Override
    public List<PublishRequest> reviewHistory(Long skillId) {
        return publishRequestMapper.selectBySkillIdOrderByCreatedAtDesc(skillId);
    }

    // ==================== 导出 ====================

    @Override
    public java.io.File export(Long skillId) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) throw new BizException("技能不存在");
        List<SkillVersion> versions = versionMapper.selectBySkillId(skillId);

        try {
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"), "skill-" + skillId);
            if (tempDir.exists()) deleteDir(tempDir);
            tempDir.mkdirs();

            String latestVer = versions.isEmpty() ? "1"
                    : versions.get(versions.size() - 1).getVersion();

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
            versions.forEach(v -> {
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

    // ==================== 内部工具 ====================

    private String upload(MultipartFile file) {
        try { return localStorageUtil.uploadPackage(file); }
        catch (Exception e) { throw new BizException("文件上传失败: " + e.getMessage()); }
    }

    private void cancelPending(Long skillId, String reason) {
        publishRequestMapper.cancelPendingBySkillId(skillId, reason, LocalDateTime.now());
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

    private String sanitize(String name) {
        if (name == null) return "unnamed";
        return name.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fff-]", "-").replaceAll("-+", "-");
    }
}