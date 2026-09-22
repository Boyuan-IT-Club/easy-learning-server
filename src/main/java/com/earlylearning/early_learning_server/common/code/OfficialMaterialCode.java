package com.earlylearning.early_learning_server.common.code;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 官方评估材料的跨端稳定编号，对应 official_material_code；具体版本由 content_version 区分。
 * <p>用于 String：有值时须非空白且不超过 64 个 Unicode 码点，保持原值与大小写。
 * null 由使用处的 NotNull 约束决定；唯一性和引用存在性由所属业务模块校验。</p>
 */
@Documented
@Target({FIELD, METHOD, PARAMETER, RECORD_COMPONENT, TYPE_USE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = {})
@ValidCode
@ReportAsSingleViolation
public @interface OfficialMaterialCode {

    String message() default "OfficialMaterialCode 必须为非空白字符串，且不超过 64 个字符";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

