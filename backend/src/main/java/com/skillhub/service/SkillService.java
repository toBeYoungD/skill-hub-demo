package com.skillhub.service;

import com.skillhub.dto.request.SkillCreateRequest;
import com.skillhub.dto.request.SkillQueryRequest;
import com.skillhub.dto.request.SkillUpdateRequest;
import com.skillhub.entity.Category;
import com.skillhub.entity.Skill;
import com.skillhub.entity.SkillVersion;
import com.skillhub.repository.CategoryRepository;
import com.skillhub.repository.SkillRepository;
import com.skillhub.repository.SkillVersionRepository;
import com.skillhub.util.LocalStorageUtil;
import com.skillhub.util.VersionUtil;
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
    private final CategoryRepository categoryRepository;
    private final LocalStorageUtil localStorageUtil;
    private final VersionUtil versionUtil;

    @Transactional
    public Skill createSkill(SkillCreateRequest request, MultipartFile iconFile,
                             MultipartFile packageFile, MultipartFile manifestFile) {

        // 1. 重名校验
        if (skillRepository.existsByName(request.getName())) {
            throw new RuntimeException("技能名称已存在: " + request.getName());
        }

        // 2. 验证分类是否存在
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("分类不存在"));
        }

        // 2. 创建技能记录
        Skill skill = new Skill();
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setDeveloper(request.getDeveloper());
        skill.setCategoryId(request.getCategoryId());
        skill.setStatus(Skill.SkillStatus.DRAFT);

        // 3. 上传图标到本地存储
        if (iconFile != null && !iconFile.isEmpty()) {
            try {
                String iconUrl = localStorageUtil.uploadIcon(iconFile);
                skill.setIconUrl(iconUrl);
            } catch (Exception e) {
                log.error("图标上传失败", e);
                throw new RuntimeException("图标上传失败: " + e.getMessage());
            }
        }

        // 4. 上传技能包
        if (packageFile != null && !packageFile.isEmpty()) {
            try {
                String packageUrl = localStorageUtil.uploadPackage(packageFile);
                skill.setPackageUrl(packageUrl);
            } catch (Exception e) {
                log.error("技能包上传失败", e);
                throw new RuntimeException("技能包上传失败: " + e.getMessage());
            }
        }

        // 5. 保存技能基本信息
        skill.setUpdatedAt(LocalDateTime.now());
        skill = skillRepository.save(skill);

        log.info("创建技能成功: {}", skill.getName());
        return skill;
    }

    @Transactional
    public SkillVersion createSkillVersion(Skill skill, String version,
                                           MultipartFile packageFile, MultipartFile manifestFile) {

        SkillVersion skillVersion = new SkillVersion();
        skillVersion.setSkill(skill);
        skillVersion.setVersion(version);

        // 上传文件到本地存储
        if (packageFile != null && !packageFile.isEmpty()) {
            try {
                String packageUrl = localStorageUtil.uploadPackage(packageFile);
                skillVersion.setPackageUrl(packageUrl);
            } catch (Exception e) {
                log.error("技能包上传失败", e);
                throw new RuntimeException("技能包上传失败: " + e.getMessage());
            }
        }

        if (manifestFile != null && !manifestFile.isEmpty()) {
            try {
                String manifestUrl = localStorageUtil.uploadManifest(manifestFile);
                skillVersion.setManifestUrl(manifestUrl);
            } catch (Exception e) {
                log.error("manifest文件上传失败", e);
                throw new RuntimeException("manifest文件上传失败: " + e.getMessage());
            }
        }

        skillVersion.setStatus(SkillVersion.VersionStatus.DRAFT);
        skillVersion = versionRepository.save(skillVersion);

        log.info("创建技能版本成功: {} - {}", skill.getName(), version);
        return skillVersion;
    }

    @Transactional
    public Skill updateSkill(Long id, SkillUpdateRequest request, MultipartFile iconFile, MultipartFile packageFile) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        if (request.getName() != null && !request.getName().isEmpty()) {
            // 重名校验，排除自身
            if (!skill.getName().equals(request.getName()) && skillRepository.existsByName(request.getName())) {
                throw new RuntimeException("技能名称已存在: " + request.getName());
            }
            skill.setName(request.getName());
        }
        if (request.getDescription() != null && !request.getDescription().isEmpty()) {
            skill.setDescription(request.getDescription());
        }
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("分类不存在"));
            skill.setCategoryId(request.getCategoryId());
        }

        if (iconFile != null && !iconFile.isEmpty()) {
            try {
                // 删除旧图标
                if (skill.getIconUrl() != null) {
                    localStorageUtil.deleteFile(skill.getIconUrl());
                }
                // 上传新图标
                String iconUrl = localStorageUtil.uploadIcon(iconFile);
                skill.setIconUrl(iconUrl);
            } catch (Exception e) {
                log.error("图标更新失败", e);
                throw new RuntimeException("图标更新失败: " + e.getMessage());
            }
        }

        if (packageFile != null && !packageFile.isEmpty()) {
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

        skill.setUpdatedAt(LocalDateTime.now());
        Skill updatedSkill = skillRepository.save(skill);
        log.info("更新技能成功: {}", skill.getName());
        return updatedSkill;
    }

    @Transactional
    public void publishSkill(Long skillId, String changelog, VersionUtil.VersionType versionType) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        // 获取最新版本号
        String latestVersion = null;
        if (!skill.getVersions().isEmpty()) {
            latestVersion = skill.getVersions().get(skill.getVersions().size() - 1).getVersion();
        }

        // 生成新版本号
        String newVersionNumber = versionUtil.generateNextVersion(latestVersion, versionType);

        // 取消所有版本的isLatest标记
        skill.getVersions().forEach(version -> version.setLatest(false));

        // 创建新版本，沿用上一版本的文件
        SkillVersion newVersion = new SkillVersion();
        newVersion.setSkill(skill);
        newVersion.setVersion(newVersionNumber);
        newVersion.setSkillNameSnapshot(skill.getName());
        newVersion.setSkillDescriptionSnapshot(skill.getDescription());
        newVersion.setIconUrlSnapshot(skill.getIconUrl());

        // 从上一版本复制文件链接；若没有上一版本则用 skill 当前的
        if (!skill.getVersions().isEmpty()) {
            SkillVersion prev = skill.getVersions().get(skill.getVersions().size() - 1);
            newVersion.setPackageUrl(prev.getPackageUrl());
            newVersion.setManifestUrl(prev.getManifestUrl());
        } else {
            newVersion.setPackageUrl(skill.getPackageUrl());
        }

        newVersion.setChangelog(changelog != null ? changelog : String.format("版本 %s 发布", newVersionNumber));
        newVersion.setStatus(SkillVersion.VersionStatus.PUBLISHED);
        newVersion.setLatest(true);

        newVersion = versionRepository.save(newVersion);
        skill.getVersions().add(newVersion);

        skill.setStatus(Skill.SkillStatus.PUBLISHED);
        skill.setLastPublishedAt(LocalDateTime.now());
        skillRepository.save(skill);

        log.info("发布技能成功: {}, 版本: {}", skill.getName(), newVersionNumber);
    }

    @Transactional
    public SkillVersion rollbackSkillVersion(Skill skill, SkillVersion targetVersion) {
        // 获取最新版本号
        String latestVersion = null;
        if (!skill.getVersions().isEmpty()) {
            latestVersion = skill.getVersions().get(skill.getVersions().size() - 1).getVersion();
        }

        // 生成新版本号（补丁版本）
        String newVersionNumber = versionUtil.generateNextVersion(latestVersion, VersionUtil.VersionType.PATCH);

        // 取消所有版本的isLatest标记
        skill.getVersions().forEach(version -> version.setLatest(false));

        // 创建回滚版本
        SkillVersion rollbackVersion = new SkillVersion();
        rollbackVersion.setSkill(skill);
        rollbackVersion.setVersion(newVersionNumber);
        rollbackVersion.setPackageUrl(targetVersion.getPackageUrl());
        rollbackVersion.setManifestUrl(targetVersion.getManifestUrl());
        rollbackVersion.setChangelog(String.format("从版本 %s 回滚", targetVersion.getVersion()));
        rollbackVersion.setStatus(SkillVersion.VersionStatus.PUBLISHED);
        rollbackVersion.setLatest(true);
        rollbackVersion.setRollback(true);
        rollbackVersion.setRolledBackFrom(targetVersion.getVersion());

        rollbackVersion = versionRepository.save(rollbackVersion);
        skill.getVersions().add(rollbackVersion);

        // 恢复快照数据
        if (targetVersion.getSkillNameSnapshot() != null) {
            skill.setName(targetVersion.getSkillNameSnapshot());
        }
        if (targetVersion.getSkillDescriptionSnapshot() != null) {
            skill.setDescription(targetVersion.getSkillDescriptionSnapshot());
        }
        if (targetVersion.getIconUrlSnapshot() != null) {
            skill.setIconUrl(targetVersion.getIconUrlSnapshot());
        }
        if (targetVersion.getPackageUrl() != null) {
            skill.setPackageUrl(targetVersion.getPackageUrl());
        }
        skill.setLastPublishedAt(LocalDateTime.now());
        skillRepository.save(skill);

        log.info("版本回滚成功: {} -> {}, 新版本: {}", targetVersion.getVersion(), newVersionNumber, newVersionNumber);
        return rollbackVersion;
    }

    @Transactional
    public void deleteSkillVersion(Skill skill, SkillVersion version) {
        // 检查是否可以删除
        if (version.isLatest() && version.getStatus() == SkillVersion.VersionStatus.PUBLISHED) {
            throw new RuntimeException("不能删除最新的已发布版本");
        }

        // 删除关联的文件
        if (version.getPackageUrl() != null) {
            localStorageUtil.deleteFile(version.getPackageUrl());
        }
        if (version.getManifestUrl() != null) {
            localStorageUtil.deleteFile(version.getManifestUrl());
        }

        // 删除版本
        skill.getVersions().remove(version);
        versionRepository.delete(version);

        // 如果删除的是最新版本，标记前一个版本为最新
        if (skill.getVersions().size() > 0) {
            SkillVersion lastVersion = skill.getVersions().get(skill.getVersions().size() - 1);
            lastVersion.setLatest(true);
            versionRepository.save(lastVersion);
        }

        skillRepository.save(skill);
        log.info("删除技能版本成功: {}", version.getVersion());
    }

    @Transactional
    public void deleteSkill(Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        // 删除本地存储的文件
        if (skill.getIconUrl() != null) {
            localStorageUtil.deleteFile(skill.getIconUrl());
        }

        skill.getVersions().forEach(version -> {
            if (version.getPackageUrl() != null) {
                localStorageUtil.deleteFile(version.getPackageUrl());
            }
            if (version.getManifestUrl() != null) {
                localStorageUtil.deleteFile(version.getManifestUrl());
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
        return skillRepository.findByStatus(Skill.SkillStatus.PUBLISHED);
    }

    public java.io.File exportSkill(Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("技能不存在"));

        try {
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"), "skill-" + skillId);
            if (tempDir.exists()) {
                deleteDir(tempDir);
            }
            tempDir.mkdirs();

            // 取最新版本号
            String latestVer = "1";
            if (!skill.getVersions().isEmpty()) {
                SkillVersion latest = skill.getVersions().get(skill.getVersions().size() - 1);
                latestVer = latest.getVersion();
            }

            // 1. SKILL.md（YAML frontmatter + 正文）
            StringBuilder md = new StringBuilder();
            md.append("---\n");
            md.append("name: ").append(sanitizeName(skill.getName())).append("\n");
            md.append("description: ").append(skill.getDescription() != null ? skill.getDescription() : "").append("\n");
            md.append("version: ").append(latestVer).append("\n");
            md.append("---\n\n");
            md.append("# ").append(skill.getName()).append("\n\n");
            if (skill.getDescription() != null) {
                md.append(skill.getDescription()).append("\n\n");
            }
            md.append("## 基本信息\n\n");
            md.append("- 开发者: ").append(skill.getDeveloper() != null ? skill.getDeveloper() : "-").append("\n");
            md.append("- 状态: ").append(skill.getStatus()).append("\n");
            md.append("- 下载次数: ").append(skill.getDownloadCount()).append("\n");
            md.append("- 使用次数: ").append(skill.getUseCount()).append("\n");
            md.append("- 创建时间: ").append(skill.getCreatedAt()).append("\n\n");

            md.append("## 版本历史\n\n");
            for (SkillVersion v : skill.getVersions()) {
                md.append("- v").append(v.getVersion())
                  .append(" (").append(v.getStatus()).append(")")
                  .append(v.isRollback() ? " [回滚]" : "")
                  .append(v.isLatest() ? " [最新]" : "").append("\n");
                if (v.getChangelog() != null) {
                    md.append("  - ").append(v.getChangelog()).append("\n");
                }
            }
            if (skill.getVersions().isEmpty()) {
                md.append("- 暂无版本\n");
            }
            java.nio.file.Files.writeString(new java.io.File(tempDir, "SKILL.md").toPath(), md.toString());

            // 2. scripts/ 目录（存放技能包）
            java.io.File scriptsDir = new java.io.File(tempDir, "scripts");
            scriptsDir.mkdirs();
            copySkillFile(skill.getPackageUrl(), new java.io.File(scriptsDir, "package"));

            // 3. resources/ 目录（存放图标）
            java.io.File resourcesDir = new java.io.File(tempDir, "resources");
            resourcesDir.mkdirs();
            copySkillFile(skill.getIconUrl(), new java.io.File(resourcesDir, "icon"));

            // 4. 打成 ZIP
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
        if (dir.isDirectory()) {
            for (java.io.File f : dir.listFiles()) {
                deleteDir(f);
            }
        }
        dir.delete();
    }
}