/**
 * 管理员模块：管理员登录、账号新建 / 列表 / 改密码 / 停用，首个管理员初始化，以及管理端 Bearer 校验。
 * 组织规范见 {@code reference/adr/0008}。
 *
 * <p>四层（依赖单向 {@code interfaces → application → domain ← infrastructure}）：
 * <ul>
 *   <li>interfaces：管理员 Controller 与请求 / 响应 DTO</li>
 *   <li>application：{@code AdminLoginService}（登录、限流、锁定）、{@code AdminAccountService}（账号维护）、
 *       {@code AdminBootstrap}（启动时初始化）、{@code AdminBearerAuthenticator}（实现 auth 的 {@code BearerAuthenticator}）</li>
 *   <li>domain：{@code AdminAccount} 实体、状态、密码规则、只读快照与登录结果</li>
 *   <li>infrastructure：{@code AdminAccountMapper}、PBKDF2 编码器、初始化配置</li>
 * </ul>
 *
 * <p>依赖 auth（签发与吊销管理员 Token）；不被其他业务模块依赖。所有管理员同权限，无 RBAC。数据边界：admin_account。
 */
package com.earlylearning.early_learning_server.admin;
