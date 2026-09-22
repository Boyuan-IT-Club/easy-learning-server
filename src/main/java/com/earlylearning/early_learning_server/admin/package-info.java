/**
 * 管理员账号与管理端鉴权。
 *
 * <p>负责管理员内部创建、账号状态与密码维护、管理员登录和管理端 API 鉴权。管理员仅一个权限级别。</p>
 *
 * <p>数据边界：admin_account。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/NU4zwM5tZiQQw4kXbiXcYjKuncf">01_账号与鉴权，3.1 管理员</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.admin;

