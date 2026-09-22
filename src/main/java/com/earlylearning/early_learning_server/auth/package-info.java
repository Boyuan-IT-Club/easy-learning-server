/**
 * 教师注册激活与云端访问鉴权。
 *
 * <p>负责注册流程编排、激活码绑定、Access Token 签发与校验、Refresh Token 轮换和云端账号状态校验。注册时协调 teacher 与 license，在同一事务内创建账号并完成绑定。教师密码和离线登录留在移动端。</p>
 *
 * <p>数据边界：通过 teacher、license 协调账号与许可证；维护 user_account.refresh_token_hash。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/NU4zwM5tZiQQw4kXbiXcYjKuncf">01_账号与鉴权，3.3 用户与鉴权</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.auth;

