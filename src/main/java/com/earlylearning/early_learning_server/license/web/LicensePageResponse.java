package com.earlylearning.early_learning_server.license.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code LicensePage}。 */
public record LicensePageResponse(@JsonProperty("items") List<LicenseResponse> items,
                                  @JsonProperty("page") int page,
                                  @JsonProperty("page_size") int pageSize,
                                  @JsonProperty("total") long total) {
}
