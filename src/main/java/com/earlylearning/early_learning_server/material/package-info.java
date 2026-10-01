/**
 * 评估材料模块（限界上下文）：ZIP 包发布成不可变内容版本，供管理端检索与平板端按版本下载。
 *
 * <p>四层组织与依赖方向见 reference/adr/0008：interfaces 只做 HTTP 形状，application 编排发布
 * 与查询用例，domain 放实体与发布规则，infrastructure 放数据库访问与 ZIP 解析。媒体文件本体
 * 不在本模块落库，走 storage 模块的上传服务，这里只持有转换后的 file_code。
 */
package com.earlylearning.early_learning_server.material;
