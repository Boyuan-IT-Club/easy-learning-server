/**
 * 跨端公共 Code 契约，字段仍使用 String，JSON 中仍为原有字符串。
 *
 * <p>五种 Code 都是开放集合，采用具名 Jakarta Validation 注解：
 * FileCode、GrammarCode、EntryCode、OfficialCourseCode、OfficialMaterialCode。
 * 对应数据库列均为 VARCHAR(64)，按 Unicode 码点计算长度，保持原值与大小写，
 * 不规定固定前缀或字符集，不执行 trim、大小写转换或 Unicode 归一化。</p>
 *
 * <p>示例：{@code record DownloadRequest(@NotNull @FileCode String file_code) {}}
 * 和 {@code List<@NotNull @GrammarCode String>}。
 * 请求对象通过 Spring MVC 的 {@code @Valid} 或 Jakarta Validator 触发校验；
 * 方法参数校验通过对应的方法校验机制触发，注解本身不会自动拦截普通方法调用。</p>
 *
 * <p>单独的 Code 注解允许 null，以兼容可选录音引用和私人课程的空官方编号。
 * 是否必填、是否存在、是否重复及是否可引用由使用处的业务规则决定。
 * OfficialCourseCode 和 OfficialMaterialCode 标识内容，具体版本仍由 content_version 区分。</p>
 */
package com.earlylearning.early_learning_server.common.code;

