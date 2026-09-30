package com.earlylearning.early_learning_server.admin.domain;

import com.earlylearning.early_learning_server.auth.domain.IssuedToken;

/** 登录成功的结果。 */
public record AdminLogin(IssuedToken token, AdminView admin) {
}
