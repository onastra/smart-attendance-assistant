package com.lianzhi.attendance.knowledge.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 知识库原文件的本地受控存储。
 *
 * <p>只保存在 {@code lz.storage.local-path} 指向的目录内，文件名用 UUID 重建，
 * 不沿用上传时的原始文件名——防止路径穿越与同名覆盖。</p>
 */
@Slf4j
@Component
public class KnowledgeFileStore {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${lz.storage.local-path:./data/files}")
    private String localPath;

    public StoredFile save(byte[] bytes, String originalName) {
        try {
            Path root = Paths.get(localPath).toAbsolutePath().normalize();
            Path dir = root.resolve("kb").resolve(LocalDate.now().format(DAY));
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + "." + extension(originalName);
            Path target = dir.resolve(name);
            Files.write(target, bytes);
            // 存相对路径：换机器部署时只要挂载点一致就能搬走
            return new StoredFile(root.relativize(target).toString().replace("\\", "/"), target);
        } catch (IOException e) {
            throw new IllegalStateException("文件写入失败：" + e.getMessage(), e);
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path root = Paths.get(localPath).toAbsolutePath().normalize();
            Path target = root.resolve(relativePath).normalize();
            // 二次校验：解析后的路径必须仍在根目录内，挡住 "../" 这类穿越
            if (!target.startsWith(root)) {
                log.warn("[KB] 拒绝删除越界路径：{}", relativePath);
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("[KB] 删除文件失败：{} {}", relativePath, e.getMessage());
        }
    }

    public Path resolve(String relativePath) {
        Path root = Paths.get(localPath).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("文件路径越界");
        }
        return target;
    }

    private static String extension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "bin";
        }
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        return ext.replaceAll("[^a-z0-9]", "");
    }

    public record StoredFile(String relativePath, Path absolutePath) {
    }
}
