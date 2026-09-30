/**
 * 激活码模块（限界上下文）：批量生成、状态机、撤销，以及注册时的锁码与占码。组织规范见 {@code reference/adr/0008}。
 *
 * <p>数据边界：user_license。明文激活码只出现在生成接口的那一次响应里，库里只有 HMAC 与末 4 位。
 * application 与 domain 通过 @NamedInterface 暴露：teacher 模块经 {@code LicenseClaimService} 占码与查状态。
 * 依据：01_账号与鉴权 v0.2。
 */
package com.earlylearning.early_learning_server.license;
