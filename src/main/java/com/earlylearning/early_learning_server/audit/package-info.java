/**
 * 管控审计模块（限界上下文）：记录管理员与账号相关的关键操作，供后台查询。组织规范见 {@code reference/adr/0008}。
 *
 * <p>只记录元数据：谁、何时、对什么做了什么、结果如何；不记录激活码、Token、密码与任何业务正文。
 * application 通过 @NamedInterface 暴露，其他模块在自己的事务里调用 {@code AuditLogService.record}。
 */
package com.earlylearning.early_learning_server.audit;
