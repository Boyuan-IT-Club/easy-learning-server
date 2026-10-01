package com.earlylearning.early_learning_server.material.service;

import com.earlylearning.early_learning_server.entity.AssessmentMaterial;
import com.earlylearning.early_learning_server.storage.model.IncomingFile;

/**
 * ZIP 发布用例：解包与校验在事务外完成，全部通过后在短事务里提交版本行——
 * 新版设 ACTIVE、同编号旧版设 DISABLED，失败不发布半成品也不动旧版。
 *
 * <p>媒体文件通过 storage 的上传服务保存成独立官方素材（各自带幂等），发布重试时
 * 同包文件直接复用，不重复占用存储。事务失败（如版本号冲突）时已上传的素材保留：
 * 它们是合法官方文件，修正后的重新发布会原样复用。
 *
 * <p>权限：管理员凭证；本模块不校验。
 */
public interface AssessmentMaterialPublishService {

    /**
     * 上传 ZIP 并发布评估材料版本。
     *
     * @return 首次发布或幂等重放得到的版本实体；同一键同包两条路径产出等价结果
     */
    AssessmentMaterial publish(String idempotencyKey, IncomingFile zip);
}
