package com.earlylearning.early_learning_server.storage.service;

import java.util.List;

import com.earlylearning.early_learning_server.storage.model.DownloadSignature;

/**
 * 批量签发只读下载地址：先校验全部编号，全部通过后统一签发，任一项不合格则整批失败。
 *
 * <p>权限：按角色限定可签发的资源范围；本模块不校验。
 */
public interface CloudFileSignatureService {

    /** @return 领域结果；转成 HTTP 形状是 controller 层的事 */
    List<DownloadSignature> sign(List<String> fileCodes);
}
