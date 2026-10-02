package com.earlylearning.early_learning_server.storage.service;

import com.earlylearning.early_learning_server.entity.CloudFile;

/**
 * 标记删除：把文件状态改为 DELETED，不改动对象存储中的对象。
 * 被引用的文件拒绝删除并保持原状态；重复删除幂等返回。
 *
 * <p>权限：管理员凭证；本模块不校验。
 */
public interface CloudFileDeletionService {

    /** @return 标记后的文件实体；转成 HTTP 形状是 controller 层的事 */
    CloudFile markDeleted(String fileCode);
}
