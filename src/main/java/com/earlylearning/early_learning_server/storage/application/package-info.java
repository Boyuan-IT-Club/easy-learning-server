/**
 * 应用层：上传（幂等/事务/补偿删除）、查询读模型、批量签发、标记删除。
 * 服务一律返回领域对象（CloudFile / FilePage / DownloadSignature），HTTP 形状是 interfaces 层的事。
 * 本包经 @NamedInterface 暴露为模块 API。
 */
@org.springframework.modulith.NamedInterface("application")
package com.earlylearning.early_learning_server.storage.application;
