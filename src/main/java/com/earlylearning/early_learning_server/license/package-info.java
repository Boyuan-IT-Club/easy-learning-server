/**
 * 激活码模块：生成（单个或批量）、查询、撤销，以及注册时的锁码与占码。组织规范见 {@code reference/adr/0008}。
 *
 * <p>四层（依赖单向 {@code interfaces → application → domain ← infrastructure}）：
 * <ul>
 *   <li>interfaces：管理端 Controller 与请求 / 响应 DTO</li>
 *   <li>application：{@code LicenseService}（管理端用例）、{@code LicenseBindingService}（供 auth、teacher 查询与占码）</li>
 *   <li>domain：{@code License} 实体、状态、激活码生成规则、只读快照与 {@code LicenseRevoked} 事件</li>
 *   <li>infrastructure：{@code LicenseMapper}</li>
 * </ul>
 *
 * <p>不依赖任何业务模块。撤销已激活的码需要同事务停用教师：通过同步事件 {@code LicenseRevoked}
 * 交给 teacher 模块处理，而不是直接调用（否则 teacher ↔ license 成环）。
 *
 * <p>数据边界：user_license。库里只存激活码的 HMAC，原码只在生成结果里出现一次。
 */
package com.earlylearning.early_learning_server.license;
