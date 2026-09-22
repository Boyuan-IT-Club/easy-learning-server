/**
 * 教师云端账号管理。
 *
 * <p>负责教师账号资料、列表查询和启用/禁用；启用前校验绑定激活码仍为 ACTIVE。向注册与鉴权流程提供账号操作和状态信息。</p>
 *
 * <p>数据边界：user_account；Token 签发与刷新流程由 auth 负责。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/NU4zwM5tZiQQw4kXbiXcYjKuncf">01_账号与鉴权，3.3 用户与鉴权</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.teacher;

