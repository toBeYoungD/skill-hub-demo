package com.skillhub.service;

import com.skillhub.exception.SkillPackageValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class DefaultSkillPackageValidator implements SkillPackageValidator {

    @Override
    public void validate(MultipartFile file) throws SkillPackageValidationException {
        List<String> errors = new ArrayList<>();
        String filename = file.getOriginalFilename();

        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            errors.add("文件必须是 .zip 格式");
            throw new SkillPackageValidationException(errors);
        }

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(file.getBytes()))) {
            boolean hasSkillMd = false;
            String topLevelDir = null;
            boolean hasTopLevelFiles = false;
            boolean hasMultipleTopDirs = false;
            String skillMdContent = null;

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = stripTrailingSlash(entry.getName());

                if (entry.isDirectory()) {
                    // 顶层目录：无 '/' 在路径中
                    if (!name.contains("/")) {
                        if (topLevelDir == null) {
                            topLevelDir = name;
                        } else if (!topLevelDir.equals(name)) {
                            hasMultipleTopDirs = true;
                        }
                    }
                } else {
                    // 文件：检查是否在顶层
                    if (!name.contains("/")) {
                        hasTopLevelFiles = true;
                    }

                    // 检查是否是 SKILL.md（路径以 SKILL.md 结尾即可）
                    if (name.endsWith("/SKILL.md") || name.equals("SKILL.md")) {
                        hasSkillMd = true;
                        skillMdContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                zis.closeEntry();
            }

            if (hasTopLevelFiles) errors.add("压缩包顶层不能有散文件，必须包含一个根目录");
            if (hasMultipleTopDirs) errors.add("压缩包只能包含一个顶层目录");
            if (!hasSkillMd) errors.add("根目录下必须包含 SKILL.md 文件");

            if (skillMdContent != null) {
                if (!skillMdContent.trim().startsWith("---"))
                    errors.add("SKILL.md 必须以 YAML frontmatter 开头（---）");
                else {
                    String content = skillMdContent.trim();
                    int secondDash = content.indexOf("---", 3);
                    if (secondDash == -1) errors.add("SKILL.md 的 YAML frontmatter 未正确关闭");
                    else {
                        String fm = content.substring(3, secondDash).trim();
                        if (!fm.contains("name:")) errors.add("SKILL.md frontmatter 中必须包含 name 字段");
                        if (!fm.contains("description:")) errors.add("SKILL.md frontmatter 中必须包含 description 字段");
                        // name 格式校验
                        String nameValue = extractYamlValue(fm, "name");
                        if (nameValue != null && nameValue.length() > 64)
                            errors.add("SKILL.md 的 name 字段不能超过 64 个字符");
                    }
                }
            }

        } catch (IOException e) {
            errors.add("无法读取压缩包: " + e.getMessage());
        }

        if (!errors.isEmpty()) throw new SkillPackageValidationException(errors);
    }

    private String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String extractYamlValue(String frontmatter, String key) {
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + ":")) {
                return trimmed.substring(key.length() + 1).trim()
                        .replaceAll("^['\"]|['\"]$", "");
            }
        }
        return null;
    }
}