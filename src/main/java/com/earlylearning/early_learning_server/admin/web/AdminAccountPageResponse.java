package com.earlylearning.early_learning_server.admin.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code AdminAccountPage}。 */
public record AdminAccountPageResponse(@JsonProperty("items") List<AdminAccountResponse> items,
                                       @JsonProperty("page") int page,
                                       @JsonProperty("page_size") int pageSize,
                                       @JsonProperty("total") long total) {
}
