package com.earlylearning.early_learning_server.license.domain;

/**
 * 一枚已绑定教师的激活码被撤销。在撤销事务内同步发布，监听者（teacher 模块）在同一事务里停用该教师
 * （契约 revokeLicense："ACTIVE 撤销与绑定教师禁用同一事务提交"）。
 */
public record LicenseRevoked(int licenseId, int userId) {
}
