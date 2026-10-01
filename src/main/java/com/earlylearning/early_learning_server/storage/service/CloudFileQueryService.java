package com.earlylearning.early_learning_server.storage.service;

import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.enums.CloudFileKind;
import com.earlylearning.early_learning_server.enums.CloudFileStatus;
import com.earlylearning.early_learning_server.storage.model.FilePage;

/**
 * 文件查询：按编号取元数据，以及带筛选与引用数的分页列表。
 *
 * <p>权限：契约对列表要求管理员凭证、对元数据按角色区分可见范围；本模块都不校验，
 * 元数据按教师语义执行状态规则（非 READY 不可读）。
 */
public interface CloudFileQueryService {

    /** @return 分页读模型；转成 HTTP 形状是 controller 层的事 */
    FilePage list(int page,
                  int pageSize,
                  String fileCode,
                  CloudFileKind fileKind,
                  CloudFileStatus status,
                  String keyword);

    /**
     * 按编号取文件实体（含 {@code objectKey} 等内部字段），供模块内部读取内容使用。
     *
     * <p>校验语义与签发、元数据接口保持一致：不存在 404、已删除 410、未就绪 409。
     * 否则会出现"评分能拿到图、下载却拿不到"这类最难查的不一致。
     */
    CloudFile requireReadable(String fileCode);

    /** @return 文件实体；转成 HTTP 形状是 controller 层的事 */
    CloudFile metadata(String fileCode);
}
