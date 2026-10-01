package com.earlylearning.early_learning_server.identity.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.idempotency.IdempotencyKeys;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.identity.dto.RefreshRequest;
import com.earlylearning.early_learning_server.identity.dto.RegisterRequest;
import com.earlylearning.early_learning_server.identity.dto.TokenPairResponse;
import com.earlylearning.early_learning_server.identity.service.TeacherRefreshService;
import com.earlylearning.early_learning_server.identity.service.TeacherRegistrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 教师注册与刷新（契约 registerTeacher、refreshTeacherToken）。两个接口都免认证。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class TeacherAuthController {

    private final TeacherRegistrationService teacherRegistrationService;
    private final TeacherRefreshService teacherRefreshService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenPairResponse> register(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody RegisterRequest request) {
        log.info("教师注册请求 username={}", request.username());
        return ApiResponse.ok(teacherRegistrationService.register(request.activationCode(), request.username(),
                IdempotencyKeys.require(idempotencyKey)));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody RefreshRequest request) {
        log.info("教师刷新凭证请求");
        return ApiResponse.ok(teacherRefreshService.refresh(request.refreshToken(),
                IdempotencyKeys.require(idempotencyKey)));
    }
}
