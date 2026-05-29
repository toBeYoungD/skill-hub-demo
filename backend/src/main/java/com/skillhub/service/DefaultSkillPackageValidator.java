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
            String skillMdContent = null;

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                int slashIdx = name.indexOf('/');
                String top = slashIdx > 0 ? name.substring(0, slashIdx) : name;

                if (entry.isDirectory() && slashIdx < 0) {
                    if (topLevelDir == null) topLevelDir = name;
                } else if (!entry.isDirectory() && !name.contains("/")) {
                    hasTopLevelFiles = true;
                }

                if (name.equals((topLevelDir != null ? topLevelDir : "") + "SKILL.md") && !entry.isDirectory()) {
                    hasSkillMd = true;
                    skillMdContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }

            if (hasTopLevelFiles) errors.add("压缩包顶层不能有散文件，必须包含一个根目录");
            if (!hasSkillMd) errors.add("根目录下必须包含 SKILL.md 文件");

            if (skillMdContent != null) {
                if (!skillMdContent.startsWith("---")) errors.add("SKILL.md 必须以 YAML frontmatter 开头（---）");
                else {
                    int secondDash = skillMdContent.indexOf("---", 3);
                    if (secondDash == -1) errors.add("SKILL.md 的 YAML frontmatter 未正确关闭");
                    else {
                        String fm = skillMdContent.substring(3, secondDash).trim();
                        if (!fm.contains("name:")) errors.add("SKILL.md frontmatter 中必须包含 name 字段");
                        if (!fm.contains("description:")) errors.add("SKILL.md frontmatter 中必须包含 description 字段");
                    }
                }
            }

        } catch (IOException e) {
            errors.add("无法读取压缩包: " + e.getMessage());
        }

        if (!errors.isEmpty()) throw new SkillPackageValidationException(errors);
    }
}