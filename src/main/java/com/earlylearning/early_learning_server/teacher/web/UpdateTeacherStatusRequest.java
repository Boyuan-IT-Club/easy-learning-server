package com.earlylearning.early_learning_server.teacher.web;

import com.earlylearning.early_learning_server.common.web.RejectUnknownFields;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code additionalProperties: false}：未知字段直接 400。 */
@RejectUnknownFields
public record UpdateTeacherStatusRequest(@JsonProperty("status") Integer status) {
}
