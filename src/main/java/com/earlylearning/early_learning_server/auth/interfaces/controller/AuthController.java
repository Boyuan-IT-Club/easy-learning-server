package com.earlylearning.early_learning_server.auth.interfaces.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.auth.application.RefreshService;
import com.earlylearning.early_learning_server.auth.application.RegistrationService;
import com.earlylearning.early_learning_server.auth.interfaces.dto.RefreshRequest;
import com.earlylearning.early_learning_server.auth.interfaces.dto.RegisterRequest;
import com.earlylearning.early_learning_server.auth.interfaces.dto.TokenPairResponse;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyKeys;
import com.earlylearning.early_learning_server.common.web.ApiResponse;

/** 教师注册与刷新（契约"教师鉴权"）。两个接口都免认证。 */
@RestController
public class AuthController {

    private final RegistrationService registration;
    private final RefreshService refresh;

    public AuthController(RegistrationService registration, RefreshService refresh) {
        this.registration = registration;
        this.refresh = refresh;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenPairResponse> register(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody RegisterRequest request) {
        return ApiResponse.ok(TokenPairResponse.from(registration.register(request.activationCode(), request.username(),
                IdempotencyKeys.require(idempotencyKey))));
    }

    @PostMapping("/api/auth/refresh")
    public ApiResponse<TokenPairResponse> refresh(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody RefreshRequest request) {
        return ApiResponse.ok(TokenPairResponse.from(
                refresh.refresh(request.refreshToken(), IdempotencyKeys.require(idempotencyKey))));
    }
}
