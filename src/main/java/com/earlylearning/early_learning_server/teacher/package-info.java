/**
 * 教师云端账号模块：账号元数据、启用 / 停用，以及供 auth 使用的账号与 refresh 哈希读写。组织规范见 {@code reference/adr/0008}。
 *
 * <p>四层（依赖单向 {@code interfaces → application → domain ← infrastructure}）：
 * <ul>
 *   <li>interfaces：管理端教师管理 Controller 与 DTO</li>
 *   <li>application：{@code TeacherAdminService}（管理端用例）、{@code TeacherAccountService}（供 auth 调用，并监听激活码撤销）</li>
 *   <li>domain：{@code TeacherAccount} 实体、状态、只读快照</li>
 *   <li>infrastructure：{@code TeacherAccountMapper}</li>
 * </ul>
 *
 * <p>依赖 license：启用前查绑定激活码是否仍为 ACTIVE；撤销已激活的码时经同步事件 {@code LicenseRevoked} 停用教师。
 * 云端不保存教师密码，教师资料只在平板本地。数据边界：user_account。
 */
package com.earlylearning.early_learning_server.teacher;
