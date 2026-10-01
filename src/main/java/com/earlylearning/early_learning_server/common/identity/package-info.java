/**
 * 契约级的账号标识规则：管理员与教师共用的 {@code Username}（3—64 位 ASCII 字母、数字、下划线、点或连字符，
 * 转小写后唯一）。不含任何账号业务。整个子包都是对外 API（@NamedInterface）。
 */
@org.springframework.modulith.NamedInterface("identity")
package com.earlylearning.early_learning_server.common.identity;
