package com.earlylearning.early_learning_server.identity.model;

import java.util.List;

/**
 * 一批新生成激活码的脱敏快照：只有 id，不含原码与哈希。写入幂等表；
 * 原码的短时重放过期后，据此告诉管理员这批码的 id，好逐个撤销（契约 createLicenses）。
 */
public record IssuedLicenseIds(List<Integer> ids) {
}
