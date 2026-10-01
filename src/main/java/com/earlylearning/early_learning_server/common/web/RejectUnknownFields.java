package com.earlylearning.early_learning_server.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求体出现未声明的字段时直接 400（契约 {@code additionalProperties: false}）。
 *
 * <p>Spring 的 ObjectMapper 默认忽略未知字段，{@code @JsonIgnoreProperties(ignoreUnknown = false)}
 * 也改变不了这一点；由 {@link StrictRequestBodies} 对带本注解的类型逐个生效，不影响其他模块。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RejectUnknownFields {
}
