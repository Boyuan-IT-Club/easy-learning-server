/**
 * 云端文件与对象存储。
 *
 * <p>负责官方文件上传、元数据、file_code 映射、SHA256 校验、文件状态、下载地址签发和允许的清理。通过统一存储抽象封装 OSS；引用是否允许删除由所属业务模块协作判断。</p>
 *
 * <p>数据边界：storage_cloud_file。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/XTvkwj6pHimslckweHKcaxmQngh">06_文件同步与AI，3.14 文件</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.storage;

