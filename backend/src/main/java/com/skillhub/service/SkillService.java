package com.skillhub.service;

import com.skillhub.dto.request.SkillCreateRequest;
import com.skillhub.dto.request.SkillQueryRequest;
import com.skillhub.dto.request.SkillUpdateRequest;
import com.skillhub.entity.PublishRequest;
import com.skillhub.entity.Skill;
import com.skillhub.entity.SkillVersion;
import com.skillhub.repository.SkillRepository;
import com.skillhub.repository.SkillVersionRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final LocalStorageUtil localStorageUtil;
    private final SkillPackageValidator skillPackageValidator;
    private final PublishRequestService publishRequestService;
    private final VisibilityService visibilityService;

    @Transactional
    public Skill createSkill(SkillCreateRequest request, MultipartFile packageFile) {

        if (skillRepository.existsByName(request.getName())) {
            throw new RuntimeException("技能名称已存在: " + request.getName());
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
            try {
                String url = localStorageUtil.uploadPackage(packageFile);
                skill.setPackageUrl(url);
            } catch (Exception e) {
                log.error("技能包上传失败", e);
                throw new RuntimeException("技能包上传失败: " + e.getMessage());
            }
        }

        skill.setUpdatedAt(LocalDateTime.now());
        skill = skillRepository.save(skill);
        log.info("创建技能成功: {}", skill.getName());
        return skill;
    }

    @Transactional
    public Skill updateSkill(Long id, SkillUpdateRequest request, MultipartFile packageFile) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        if (request.getName() != null && !request.getName().isEmpty()
                && !skill.getName().equals(request.getName())
                && skillRepository.existsByName(request.getName())) {
            throw new RuntimeException("技能名称已存在: " + request.getName());
        }

        if (request.getName() != null && !request.getName().isEmpty()) {
            skill.setName(request.getName());
        }
        if (request.getDescription() != null && !request.getDescription().isEmpty()) {
            skill.setDescription(request.getDescription());
        }

        if (packageFile != null && !packageFile.isEmpty()) {
            skillPackageValidator.validate(packageFile);
            try {
                if (skill.getPackageUrl() != null) {
                    localStorageUtil.deleteFile(skill.getPackageUrl());
                }
                String url = localStorageUtil.uploadPackage(packageFile);
                skill.setPackageUrl(url);
            } catch (Exception e) {
                log.error("技能包更新失败", e);
                throw new RuntimeException("技能包更新失败: " + e.getMessage());
            }
        }

        // 编辑时如果技能处于 PENDING_REVIEW，作废待审批的申请
        if (skill.getStatus() == Skill.SkillStatus.PENDING_REVIEW) {
            publishRequestService.cancelPendingRequests(skill.getId(), "技能内容已变更，旧申请自动作废");
            skill.setStatus(Skill.SkillStatus.DRAFT);
        }

        skill.setUpdatedAt(LocalDateTime.now());
        skill = skillRepository.save(skill);
        log.info("更新技能成功: {}", skill.getName());
        return skill;
    }

    @Transactional
    public Skill saveSkill(Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("技能不存在"));
        skill.setStatus(Skill.SkillStatus.DRAFT);
        skill.setUpdatedAt(LocalDateTime.now());
        skill = skillRepository.save(skill);
        log.info("保存技能为草稿: {}", skill.getName());
        return skill;
    }

    @Transactional
    public void submitReview(Long skillId, String changelog) {
        publishRequestService.submitReview(skillId, changelog);
    }

    public List<PublishRequest> getReviewHistory(Long skillId) {
        return publishRequestService.getReviewHistory(skillId);
    }

    @Transactional
    public void deleteSkill(Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        if (skill.getStatus() != Skill.SkillStatus.DRAFT && skill.getStatus() != Skill.SkillStatus.REJECTED) {
            throw new RuntimeException("只能删除草稿或未通过状态的技能，当前状态: " + skill.getStatus());
        }

        if (skill.getPackageUrl() != null) {
            localStorageUtil.deleteFile(skill.getPackageUrl());
        }

        skill.getVersions().forEach(version -> {
            if (version.getPackageUrl() != null) {
                localStorageUtil.deleteFile(version.getPackageUrl());
            }
        });

        skillRepository.delete(skill);
        log.info("删除技能成功: {}", skill.getName());
    }

    public Page<Skill> getSkills(SkillQueryRequest request, Pageable pageable) {
        if (request.getStatus() != null) {
            if (request.getName() != null && !request.getName().isEmpty()) {
                return skillRepository.findAll(
                        (root, query, cb) -> cb.and(
                                cb.equal(root.get("status"), request.getStatus()),
                                cb.like(root.get("name"), "%" + request.getName() + "%")
                        ),
                        pageable
                );
            }
            return skillRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("status"), request.getStatus()),
                    pageable
            );
        }

        if (request.getName() != null && !request.getName().isEmpty()) {
            return skillRepository.findAll(
                    (root, query, cb) -> cb.like(root.get("name"), "%" + request.getName() + "%"),
                    pageable
            );
        }

        return skillRepository.findAll(pageable);
    }

    public Skill getSkillDetail(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("技能不存在"));
    }

    @Transactional
    public void incrementDownloadCount(Long skillId) {
        skillRepository.incrementDownloadCount(skillId);
    }

    @Transactional
    public void incrementUseCount(Long skillId) {
        skillRepository.incrementUseCount(skillId);
    }

    public List<Skill> getPublishedSkills() {
        return skillRepository.findByStatusAndDelistedFalse(Skill.SkillStatus.PUBLISHED);
    }

    @Transactional
    public SkillVersion rollbackSkillVersion(Skill skill, SkillVersion targetVersion) {
        String latestVersion = null;
        if (!skill.getVersions().isEmpty())
            latestVersion = skill.getVersions().get(skill.getVersions().size() - 1).getVersion();
        String newVersion = String.valueOf(latestVersion == null ? 1 : Integer.parseInt(latestVersion) + 1);

        skill.getVersions().forEach(v -> v.setLatest(false));

        SkillVersion rollbackVersion = new SkillVersion();
        rollbackVersion.setSkill(skill);
        rollbackVersion.setVersion(newVersion);
        rollbackVersion.setPackageUrl(targetVersion.getPackageUrl());
        rollbackVersion.setManifestUrl(targetVersion.getManifestUrl());
        rollbackVersion.setChangelog("从版本 " + targetVersion.getVersion() + " 回滚");
        rollbackVersion.setStatus(SkillVersion.VersionStatus.PUBLISHED);
        rollbackVersion.setLatest(true);
        rollbackVersion.setRollback(true);
        rollbackVersion.setRolledBackFrom(targetVersion.getVersion());

        skill.getVersions().add(rollbackVersion);

        if (targetVersion.getSkillNameSnapshot() != null) skill.setName(targetVersion.getSkillNameSnapshot());
        if (targetVersion.getSkillDescriptionSnapshot() != null) skill.setDescription(targetVersion.getSkillDescriptionSnapshot());
        if (targetVersion.getPackageUrl() != null) skill.setPackageUrl(targetVersion.getPackageUrl());
        skill.setLastPublishedAt(LocalDateTime.now());
        skillRepository.save(skill);

        return rollbackVersion;
    }

    @Transactional
    public void deleteSkillVersion(Skill skill, SkillVersion version) {
        if (version.isLatest() && version.getStatus() == SkillVersion.VersionStatus.PUBLISHED)
            throw new RuntimeException("不能删除最新的已发布版本");
        if (version.getPackageUrl() != null) localStorageUtil.deleteFile(version.getPackageUrl());
        skill.getVersions().remove(version);
        if (!skill.getVersions().isEmpty())
            skill.getVersions().get(skill.getVersions().size() - 1).setLatest(true);
        skillRepository.save(skill);
    }

    public java.io.File exportSkill(Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        try {
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"), "skill-" + skillId);
            if (tempDir.exists()) deleteDir(tempDir);
            tempDir.mkdirs();

            String latestVer = "1";
            if (!skill.getVersions().isEmpty()) {
                latestVer = skill.getVersions().get(skill.getVersions().size() - 1).getVersion();
            }

            StringBuilder md = new StringBuilder();
            md.append("---\n");
            md.append("name: ").append(sanitizeName(skill.getName())).append("\n");
            md.append("description: ").append(skill.getDescription() != null ? skill.getDescription() : "").append("\n");
            md.append("version: ").append(latestVer).append("\n");
            md.append("---\n\n");
            md.append("# ").append(skill.getName()).append("\n\n");
            if (skill.getDescription() != null) md.append(skill.getDescription()).append("\n\n");
            md.append("## 基本信息\n\n");
            md.append("- 开发者: ").append(skill.getDeveloper() != null ? skill.getDeveloper() : "-").append("\n");
            md.append("- 状态: ").append(skill.getStatus()).append("\n");
            md.append("- 下载次数: ").append(skill.getDownloadCount()).append("\n");
            md.append("- 使用次数: ").append(skill.getUseCount()).append("\n\n");
            md.append("## 版本历史\n\n");
            for (SkillVersion v : skill.getVersions()) {
                md.append("- v").append(v.getVersion()).append(" (").append(v.getStatus()).append(")")
                  .append(v.isRollback() ? " [回滚]" : "").append(v.isLatest() ? " [最新]" : "").append("\n");
                if (v.getChangelog() != null) md.append("  - ").append(v.getChangelog()).append("\n");
            }
            java.nio.file.Files.writeString(new java.io.File(tempDir, "SKILL.md").toPath(), md.toString());

            java.io.File scriptsDir = new java.io.File(tempDir, "scripts");
            scriptsDir.mkdirs();
            copySkillFile(skill.getPackageUrl(), new java.io.File(scriptsDir, "package"));

            java.io.File zipFile = new java.io.File(tempDir.getParent(), "skill-" + skillId + ".zip");
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    new java.io.FileOutputStream(zipFile))) {
                zipDir(tempDir, tempDir, zos);
            }

            deleteDir(tempDir);
            return zipFile;
        } catch (Exception e) {
            log.error("导出技能失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    private String sanitizeName(String name) {
        if (name == null) return "unnamed";
        return name.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fff-]", "-").replaceAll("-+", "-");
    }

    private void copySkillFile(String relativePath, java.io.File dest) {
        if (relativePath == null) return;
        try (java.io.InputStream in = localStorageUtil.getFileInputStream(relativePath)) {
            java.nio.file.Files.copy(in, dest.toPath());
        } catch (Exception e) {
            log.warn("复制文件失败: {}", relativePath, e);
        }
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
}