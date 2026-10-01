package com.earlylearning.early_learning_server.teacher.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code TeacherPage}。 */
public record TeacherPageResponse(@JsonProperty("items") List<UserAccountResponse> items,
                                  @JsonProperty("page") int page,
                                  @JsonProperty("page_size") int pageSize,
                                  @JsonProperty("total") long total) {
}
