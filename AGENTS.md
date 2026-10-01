# AGENTS.md

## 1. 开发前置

修改代码前必须先阅读与当前任务相关的 `docs/` 文档。

`docs/` 中的技术方案和功能细则是业务规则、数据库结构、JSON 协议和接口边界的主要依据。

如果以下内容存在冲突：

- 用户当前要求
- `docs/` 文档
- 现有代码

不要自行猜测或静默修正，先指出冲突。

---

## 2. 总体开发原则

- 仅修改当前任务必要范围。
- 不擅自新增业务规则。
- 不为了“架构完整”进行无关重构。
- 不主动引入微服务、消息队列、分布式事务等当前未要求的基础设施。Redis 仅用于短时数据（Token、刷新宽限、敏感结果重放、限流），须关闭持久化，不存业务数据。
- 优先使用现有技术栈和已有组件。
- 新增依赖前先确认现有依赖无法合理完成任务。
- Controller 保持薄，业务逻辑进入 Service 或对应业务组件。
- 不机械拆类，也不允许单个 Service 无限膨胀。

---

## 3. 模块组织

由 `ArchitectureTests` 强制执行（违反即测试红）。

**第一层：按业务模块分包。** 当前模块：

```text
identity/  ← 账号与鉴权：管理员、激活码、教师账号、教师注册与刷新
security/  ← 鉴权机制：安全链、Token 签发 / 校验 / 吊销、已认证身份类型（不含账号业务）
ai/        ← 录音转写与评分
material/  ← 评估材料（ZIP 发布成内容版本，供下载同步）
storage/   ← 官方资源文件（对象存储）
entity/    ← 全部表映射实体与枚举，集中存放，任何模块都可以用；只依赖 common
common/    ← 共享能力，只被依赖、不依赖任何模块与 entity，任何模块都可以用：
             web（响应信封、分页响应）/ error / idempotency / logging / media / paging /
             identity（用户名规则）/ secret / ratelimit / tx / time
```

模块依赖单向、无环：`identity → security`；`ai → storage`；`material → storage, ai`。

业务代码应留在所属业务模块；跨业务复用的机械能力才抽成公共模块。不要建立一个掌握所有课程、评估、字典规则的巨大 `contentimport` 模块——将来做 `course/importer/`、`assessment/importer/` 时，各自负责对应资源的解析和业务校验。

**第二层：模块内部按职责分包，入口与层次一眼可见：**

```text
<module>/
├── controller/   入口：HTTP 接口（以后有 MQ、定时任务，在同级加 listener/、job/；security 的过滤器在 filter/）
├── dto/          请求与响应（契约的 JSON 形状，Jackson 注解只在这里）
├── service/      业务接口 XxxService（不加 I 前缀），以及实现要用到的组件（校验器、执行器等）
│   └── impl/     XxxServiceImpl：业务逻辑、事务、幂等、限流、编排
├── mapper/       MyBatis Mapper；这个模块的表只由这里的 Mapper 读写
├── model/        （可选）不落库的业务对象与规则，以及外部能力的接口（如 ChatModel、ObjectStorageService）
├── client/       （可选）外部系统与底层 I/O 适配：OSS、ECNU、Redis、ZIP / JSON 解析；实现 model 里的接口
└── config/       （可选）Spring 装配与配置项
```

依赖规则：

- controller 只调 service 接口，使用 dto（可用 entity / model 做转换）；**不碰 mapper、client**，不写业务。
- service 可以用 mapper、entity、model、client、dto；**不产出 `ApiResponse` / `ResponseEntity`**，包络由 controller 套。
- **service 一律接口 + 实现**：`service/XxxService` 是接口，`service/impl/XxxServiceImpl` 加 `@Service` 并实现它；
  除 impl 自己外谁都不依赖 impl（注入一律用接口）。接口写契约语义（做什么、失败返回什么），
  实现细节（锁、事务边界、并发处理）写在 impl 上；impl 方法只加 `@Override`，不重复接口注释；常量放 impl 里。
- **实体集中在顶层 `entity/`**，状态迁移规则写在实体方法里（如 License.claimBy、TeacherAccount.ensureCanEnable）。
  表归属由 Mapper 决定：同一实体的 BaseMapper 只能出现在一个模块，别的模块经该模块的 service 读写。
- **Controller 不把实体直接返回给客户端**：返回类型（含泛型参数）里不得出现 entity。
- entity 只依赖 common；model 不依赖任何上层（controller、filter、dto、service、mapper、client、config）；两者都不依赖 Spring Web。
- dto、mapper 不依赖 service、client。
- 跨模块只能用 `ArchitectureTests.CROSS_MODULE_API` 白名单里的包，模块之间不许成环。当前白名单：
  `ai.service.rubric`、`storage.service`、`storage.model`、`security.service`、`security.model`。
  新增跨模块依赖要先改白名单并写明谁在用，评审时看得见；不使用 package-info 或注解声明。
- 事务只开在 service 上；Redis、MQ、OSS 等外部写入放在事务外或提交之后（`AfterCommit`）。
- service 之外，**存在第二个真实实现才立接口**（如 `ChatModel`：ecnu + fake；`BearerAuthenticator`：教师 + 管理员）。
- 含敏感字段的实体（密码哈希、refresh 哈希、激活码哈希）不离开 service，对外与幂等快照都用 dto。
- `ArchitectureTests` 同时扫描源码 import：只在 Javadoc 里出现的 import 不进字节码、ArchUnit 看不到，
  但同样让 entity / model 指向上层，一律改用 `{@code}` 引用。

---

## 4. 数据库

数据库 Schema 已经过上游设计。

除非新需求与现有 Schema 存在明确冲突，否则：

- 不擅自改表。
- 不擅自拆表或合表。
- 不因为个人偏好改变 JSON / 关系表方案。
- 不修改已经执行过的 Flyway Migration。

数据库结构变更必须通过新的 Flyway Migration：

```text
V2__xxx.sql
V3__xxx.sql
```

简单 CRUD 使用 MyBatis-Plus；复杂查询允许显式 SQL。

---

## 5. JSON / Code / 协议

技术方案中已有的 JSON Schema、Code 和跨端协议视为既有契约。

包括但不限于：

```text
FileCode
GrammarCode
EntryCode
OfficialCourseCode
OfficialMaterialCode
```

不要：

- 擅自重命名字段。
- 改变已有字段语义。
- 改变评分口径。
- 改变 Code 的引用关系。
- 为了方便后端实现修改移动端协议。

如确需修改协议，先指出影响范围。

---

## 6. 资源导入

课程、评估、字典的具体导入规则分别归所属业务模块。

统一遵循：

```text
接收资源
→ 解析
→ 完整校验
→ 写 OSS
→ 写 MySQL
```

优先做到：

> 先验证，后产生正式数据。

导入时应检查必要的：

- 配置结构
- Code 唯一性
- 文件引用
- 资源完整性
- 业务约束

MySQL 使用事务。

OSS 与 MySQL 无统一事务；发生部分失败时使用补偿清理，不引入分布式事务框架。

---

## 7. OSS

业务模块不要直接散落 OSS SDK 调用。

统一经过存储抽象，例如：

```text
ObjectStorageService
```

MySQL 保存文件元数据和对象引用；OSS 保存实际大文件。

禁止硬编码 AccessKey、数据库密码、Token 或其他密钥。

---

## 8. 鉴权与安全

不要使用 Spring Security 默认生成用户作为正式鉴权方案。

管理员和教师端鉴权规则以技术方案为准。

特别注意：

- 不记录明文密码。
- 不记录完整 access token / refresh token。
- refresh token 按既定方案安全存储。
- 不把内部 Entity 无条件直接返回给客户端。
- 不在日志中输出敏感信息。

---

## 9. 前后端边界

后端负责业务规则。

不要把以下逻辑推给 React 管理端或移动端：

- 完整资源解析
- 业务规则校验
- OSS / MySQL 一致性处理
- API 使用量统计
- Token 有效性判断

前端只负责必要的输入、展示和基础客户端校验。

---

## 10. 修改前检查

实现功能前快速确认：

1. 对应 `docs/` 是否已有明确设计。
2. 功能属于哪个业务模块。
3. 是否影响数据库 Schema。
4. 是否影响 JSON / Code / API 契约。
5. 是否重复已有能力。
6. 是否错误地把业务逻辑抽进公共模块。
7. 是否存在更上游的设计问题。

如果上游设计会明显影响正确性、查询、统计、一致性或可维护性，先指出问题，再继续实现。

---

## 11. 完成任务前

至少确认：

- 项目能够编译。
- 相关测试通过。
- 没有提交真实密钥。
- 没有无关改动。
- 没有擅自修改既有业务契约。
- 涉及资源导入、数据库或鉴权时检查失败路径。

---

## 12. 项目文档

开发对应模块前，优先阅读相关文档。

### 技术方案

- `docs/<技术方案文档>`

### 功能细则

- `docs/<功能细则1>`
- `docs/<功能细则2>`
- `docs/<功能细则3>`
- `docs/<功能细则4>`
- `docs/<功能细则5>`
- `docs/<功能细则6>`
- `docs/<功能细则7>`
- `docs/<功能细则8>`
