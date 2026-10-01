/**
 * 账号与鉴权模块：管理员、激活码、教师账号，以及教师注册与刷新。代码组织见 AGENTS.md 第 3 节。
 *
 * <pre>
 *   controller/  入口：AdminAuth（登录）、AdminAccount、License、Teacher（管理端）、TeacherAuth（注册、刷新）
 *   dto/         请求与响应
 *   service/     业务、事务、幂等、限流；两个 BearerAuthenticator（教师、管理员）
 *   entity/      表映射：AdminAccount、License、TeacherAccount 及状态枚举；状态迁移规则写在实体方法里
 *   mapper/      MyBatis Mapper（状态变更都带旧状态条件）
 *   model/       不落库的规则与对象：密码规则、激活码规则、刷新宽限、激活码 id 快照
 *   client/      Redis 中的刷新宽限
 *   config/      PBKDF2 编码器、首个管理员配置
 * </pre>
 *
 * <p>数据边界：admin_account、user_account、user_license。依赖 security（签发 Token、实现认证）。
 * 云端不保存教师密码，教师资料只在平板本地。不对外暴露任何包：其他模块不需要账号业务。
 */
package com.earlylearning.early_learning_server.identity;
