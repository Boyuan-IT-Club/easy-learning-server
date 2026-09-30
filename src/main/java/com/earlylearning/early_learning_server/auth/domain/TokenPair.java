package com.earlylearning.early_learning_server.auth.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 教师的一对凭证：access_token 与 refresh_token。 */
public record TokenPair(@JsonProperty("access") IssuedToken access,
                        @JsonProperty("refresh") IssuedToken refresh) {
}
