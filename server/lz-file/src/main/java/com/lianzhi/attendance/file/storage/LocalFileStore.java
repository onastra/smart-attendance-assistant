package com.lianzhi.attendance.file.storage;

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
 * 本地受控存储。
 *
 * <p>三条硬约束，缺一条就等于把文件系统暴露给调用方：
 * <ol>
 *   <li>落盘目录固定为 {@code lz.storage.local-path}，文件名用 UUID 重建，
 *       不沿用上传时的原始文件名——挡住路径穿越与同名覆盖；</li>
 *   <li>对外只存相对路径，真实路径不下发到前端，下载必须走 id 查库；</li>
 *   <li>解析相对路径后二次校验仍在根目录内（防止库里被写入 "../" 也能读到外面）。</li>
 * </ol>
 */
@Slf4j
@Component
public class LocalFileStore {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${lz.storage.local-path:./data/files}")
    private String localPath;

    /**
     * @param category 业务子目录，例如 kb（知识库）、common（通用上传）
     */
    public StoredFile save(byte[] bytes, String originalName, String category) {
        try {
            Path root = root();
            Path dir = root.resolve(category == null ? "common" : category)
                    .resolve(LocalDate.now().format(DAY));
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + "." + extension(originalName);
            Path target = dir.resolve(name);
            Files.write(target, bytes);
            return new StoredFile(root.relativize(target).toString().replace("\\", "/"), target);
        } catch (IOException e) {
            throw new IllegalStateException("文件写入失败：" + e.getMessage(), e);
        }
    }

    /** 把库里的相对路径解析成绝对路径；越界直接抛异常，不做"尽力而为"的容错 */
    public Path resolve(String relativePath) {
        Path root = root();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            log.warn("[FILE] 拒绝越界路径解析：{}", relativePath);
            throw new IllegalArgumentException("文件路径越界");
        }
        return target;
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("[FILE] 删除文件失败：{} {}", relativePath, e.getMessage());
        }
    }

    private Path root() {
        return Paths.get(localPath).toAbsolutePath().normalize();
    }

    /** 扩展名只保留字母数字：避免 "a.txt/../../x" 这类被当成文件名一部分的情况 */
    private static String extension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "bin";
        }
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        String cleaned = ext.replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() ? "bin" : cleaned;
    }

    public record StoredFile(String relativePath, Path absolutePath) {
    }
}
