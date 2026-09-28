package com.earlylearning.early_learning_server.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;

/**
 * 对象存储能力。文件元数据、业务校验与数据库事务由调用方负责。
 *
 * <p>失败时抛 {@link com.earlylearning.early_learning_server.common.error.BusinessException}（DEPENDENCY_UNAVAILABLE）。
 */
public interface ObjectStorageService {

    /**
     * 上传到指定对象路径。调用方负责提供准确的长度，并在调用结束后关闭输入流。
     * 同一路径会覆盖已有对象；上层应为新文件分配独立路径，以便安全补偿清理。
     */
    void upload(String objectKey, InputStream input, long contentLength, String contentType);

    /** 删除对象；对象不存在时也视为成功。业务引用检查由调用方完成。 */
    void delete(String objectKey);

    /**
     * 读回对象内容。
     *
     * <p>存在的理由：评分要「按 `file_code` 取回图片内容」交给多模态模型，而服务端此前只会写、不会读。
     *
     * <p>**调用方必须先校验大小上限**：这里会把整个对象读进内存（对象大小在 `storage_cloud_file.size_bytes` 里，
     * 端口层拿不到，也不该由它决定业务上限）。失败抛 {@code BusinessException(DEPENDENCY_UNAVAILABLE)}。
     */
    byte[] read(String objectKey);

    /**
     * 签发临时 GET 下载地址，不检查对象是否存在；调用方须先完成访问权限校验。
     *
     * <p>返回**地址与它实际到期的时刻**，而不是只返回地址：契约要求把 `expires_at`（实际到期时间）
     * 回给客户端，而这个时刻只有签发方知道。让调用方自己再算一遍时间必然与真实值漂移。
     */
    DownloadUrl generateDownloadUrl(String objectKey);

    /**
     * 一次签发的结果。
     *
     * @param url       只读预签名地址；**不得写入日志，也不得持久化**
     * @param expiresAt 该地址实际失效的时刻
     */
    record DownloadUrl(URI url, Instant expiresAt) {
    }
}
