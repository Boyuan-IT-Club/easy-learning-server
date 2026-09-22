/**
 * 激活码管理。
 *
 * <p>负责单个或批量生成、状态查询、绑定状态维护和撤销。撤销 ACTIVE 激活码时协调 teacher，在同一事务内禁用对应账号。</p>
 *
 * <p>数据边界：user_license。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/NU4zwM5tZiQQw4kXbiXcYjKuncf">01_账号与鉴权，3.2 激活码管理</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.license;

