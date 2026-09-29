/**
 * 官方资源文件目录模块（限界上下文）：上传、查询、签发、标记删除。组织规范见 {@code reference/adr/0008}。
 *
 * <p>四层解剖（依赖单向 {@code interfaces → application → domain ← infrastructure}）：
 * application 与 domain 通过 @NamedInterface 对外暴露，别的模块（如 ai 取评分图片）只走这两处。
 */
package com.earlylearning.early_learning_server.storage;
