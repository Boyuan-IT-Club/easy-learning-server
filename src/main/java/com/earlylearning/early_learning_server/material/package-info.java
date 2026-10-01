/**
 * 评估材料模块：ZIP 包发布成不可变内容版本，供管理端检索与平板端按版本下载。代码组织见 AGENTS.md 第 3 节。
 *
 * <pre>
 *   controller/  入口：管理端发布、列表、停用；平板端版本目录与下载
 *   dto/         请求与响应
 *   service/     发布与查询的编排、配置校验
 *   entity/      表映射：AssessmentMaterial 及内容状态
 *   model/       不落库的业务对象：活动配置（config）、发布上限、列表与下载快照
 *   mapper/      MyBatis Mapper
 *   client/      ZIP 读取与配置 JSON 解析
 * </pre>
 *
 * <p>媒体文件本体不在本模块落库，走 storage 的上传服务，这里只持有转换后的 file_code。
 */
package com.earlylearning.early_learning_server.material;
