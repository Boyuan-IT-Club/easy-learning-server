package com.earlylearning.early_learning_server.storage;

import java.io.InputStream;
import java.net.URI;

/**
 * 对象存储能力；文件元数据、业务校验和数据库事务由上层负责。
 * SDK 操作失败时抛出 IllegalStateException，并保留原始原因。
 */
public interface ObjectStorageService {

    /**
     * 上传到指定对象路径。调用方负责提供准确的长度，并在调用结束后关闭输入流。
     * 同一路径会覆盖已有对象；上层应为新文件分配独立路径，以便安全补偿清理。
     */
    void upload(String objectKey, InputStream input, long contentLength, String contentType);

    /** 删除对象；对象不存在时也视为成功。业务引用检查由调用方完成。 */
    void delete(String objectKey);

    /** 签发临时 GET 下载地址，不检查对象是否存在；调用方须先完成访问权限校验。 */
    URI generateDownloadUrl(String objectKey);
}
