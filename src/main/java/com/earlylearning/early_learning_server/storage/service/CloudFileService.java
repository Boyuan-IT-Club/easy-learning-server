package com.earlylearning.early_learning_server.storage.service;

import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.enums.CloudFileKind;
import com.earlylearning.early_learning_server.storage.model.IncomingFile;

/**
 * 官方文件上传：校验类型与上限、算 SHA256、写对象存储、落库，并保证同一幂等键的重试返回首次结果。
 *
 * <p>上传前先落盘：既要算出摘要，又要让交给对象存储的流可重放。
 *
 * <p>返回领域对象 {@link CloudFile}：幂等快照存的是它的 JSON，首次与重放两条路径都由
 * controller 层用同一映射转成 HTTP 响应，因此表现一致。HTTP 是 web 层的事，这里不碰。
 *
 * <p>权限：管理员凭证；本模块不校验。
 */
public interface CloudFileService {

    /**
     * 上传一个官方文件。
     *
     * @return 首次执行或幂等重放得到的文件实体；同一键同输入的两条路径产出等价结果
     */
    CloudFile upload(String idempotencyKey, IncomingFile file, CloudFileKind declaredKind, String overrideFileName);
}
