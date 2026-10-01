package com.earlylearning.early_learning_server.identity.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.identity.dto.AdminLoginRequest;
import com.earlylearning.early_learning_server.identity.dto.AdminSessionResponse;
import com.earlylearning.early_learning_server.identity.service.AdminLoginService;

import lombok.RequiredArgsConstructor;

/** 管理员登录（契约 adminLogin）。免认证。 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminLoginService adminLoginService;

    @PostMapping("/login")
    public ApiResponse<AdminSessionResponse> login(@RequestBody AdminLoginRequest request) {
        return ApiResponse.ok(adminLoginService.login(request.username(), request.password()));
    }
}
