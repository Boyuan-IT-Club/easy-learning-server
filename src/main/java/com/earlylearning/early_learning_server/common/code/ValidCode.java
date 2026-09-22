package com.earlylearning.early_learning_server.common.code;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 五个公共 Code 注解复用的字符串约束；业务字段使用对应的具名 Code 注解。
 */
@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = CodeValidator.class)
public @interface ValidCode {

    String message() default "Code 必须为非空白字符串，且不超过 64 个字符";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

