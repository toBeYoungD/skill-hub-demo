package com.skillhub.util;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@Slf4j
public class LocalStorageUtil {

    @Value("${storage.base-path}")
    private String basePath;

    @Value("${storage.icons-path}")
    private String iconsPath;

    @Value("${storage.packages-path}")
    private String packagesPath;

    @Value("${storage.manifests-path}")
    private String manifestsPath;

    private Path baseDirectory;

    @PostConstruct
    public void init() {
        try {
            baseDirectory = Paths.get(basePath).toAbsolutePath().normalize();
            if (!Files.exists(baseDirectory)) {
                Files.createDirectories(baseDirectory);
                log.info("创建存储目录: {}", baseDirectory);
            }

            // 创建子目录
            createSubDirectory(iconsPath);
            createSubDirectory(packagesPath);
            createSubDirectory(manifestsPath);

            log.info("本地文件存储初始化完成，根目录: {}", baseDirectory);
        } catch (IOException e) {
            log.error("初始化本地存储失败", e);
            throw new RuntimeException("初始化本地存储失败", e);
        }
    }

    private void createSubDirectory(String subPath) throws IOException {
        Path directory = baseDirectory.resolve(subPath);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            log.info("创建子目录: {}", directory);
        }
    }

    public String uploadIcon(MultipartFile file) throws IOException {
        return uploadFile(file, iconsPath, "icon_");
    }

    public String uploadPackage(MultipartFile file) throws IOException {
        return uploadFile(file, packagesPath, "package_");
    }

    public String uploadManifest(MultipartFile file) throws IOException {
        return uploadFile(file, manifestsPath, "manifest_");
    }

    private String uploadFile(MultipartFile file, String subPath, String prefix) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String fileName = prefix + UUID.randomUUID().toString() + extension;

        Path targetPath = baseDirectory.resolve(subPath).resolve(fileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = subPath + "/" + fileName;
        log.info("文件上传成功: {}", relativePath);
        return relativePath;
    }

    public void deleteFile(String relativePath) {
        if (relativePath != null && !relativePath.isEmpty()) {
            try {
                Path targetPath = baseDirectory.resolve(relativePath).normalize();
                if (Files.exists(targetPath) && !targetPath.startsWith(baseDirectory)) {
                    log.warn("尝试删除非法路径的文件: {}", relativePath);
                    return;
                }

                if (Files.exists(targetPath)) {
                    Files.delete(targetPath);
                    log.info("文件删除成功: {}", relativePath);
                }
            } catch (IOException e) {
                log.error("删除文件失败: {}", relativePath, e);
            }
        }
    }

    public InputStream getFileInputStream(String relativePath) throws IOException {
        Path targetPath = baseDirectory.resolve(relativePath).normalize();
        if (!Files.exists(targetPath)) {
            throw new FileNotFoundException("文件不存在: " + relativePath);
        }
        return Files.newInputStream(targetPath);
    }

    public String getBaseDirectory() {
        return baseDirectory.toString();
    }

    public Path resolvePath(String relativePath) {
        return baseDirectory.resolve(relativePath);
    }

    @PreDestroy
    public void destroy() {
        log.info("本地文件存储关闭");
    }
}