package com.earlylearning.early_learning_server.license.web;

import com.earlylearning.early_learning_server.common.web.RejectUnknownFields;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code additionalProperties: false}：未知字段直接 400。 */
@RejectUnknownFields
public record CreateLicensesRequest(@JsonProperty("count") Integer count) {
}
