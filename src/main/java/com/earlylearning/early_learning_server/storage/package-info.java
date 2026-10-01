/**
 * 官方资源文件模块：上传、查询、签发下载地址、标记删除。代码组织见 AGENTS.md 第 3 节。
 *
 * <pre>
 *   controller/  入口：管理端上传与列表、文件元数据与签发
 *   dto/         请求与响应
 *   service/     上传（幂等、事务、补偿删除）、查询、签发、删除
 *   entity/      表映射：CloudFile 及其种类、状态
 *   model/       不落库的业务对象：文件编码与对象键规则、上传上限、列表快照、对象存储接口
 *   mapper/      MyBatis Mapper
 *   client/      阿里云 OSS 适配与配置（实现 model 里的 ObjectStorageService）
 * </pre>
 *
 * <p>对外暴露 service、entity、model：ai 取评分图片、material 上传包内素材都经由这里。
 */
package com.earlylearning.early_learning_server.storage;
