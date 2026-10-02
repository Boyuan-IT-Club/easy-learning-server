package com.earlylearning.early_learning_server.storage.model;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 一次上传的原始文件，service 层的入参形态。
 *
 * <p>HTTP 层把 multipart 适配成这个记录：application 只知道"一个有名有大小的字节来源"，
 * 不认识 {@code MultipartFile}，也就不依赖 Spring Web。
 *
 * @param size                字节数
 * @param originalFilename    客户端声明的原始文件名
 * @param declaredContentType 客户端声明的 MIME；{@code null} 或 octet-stream 视为"没声明"
 * @param stager              把内容落到指定路径（供算摘要、交给对象存储的流可重放）
 */
public record IncomingFile(long size,
                           String originalFilename,
                           String declaredContentType,
                           Stager stager) {

    /** 把上传内容写到 {@code target}；由 HTTP 层用 multipart 的 transferTo 实现。 */
    @FunctionalInterface
    public interface Stager {
        void stageTo(Path target) throws IOException;
    }
}
