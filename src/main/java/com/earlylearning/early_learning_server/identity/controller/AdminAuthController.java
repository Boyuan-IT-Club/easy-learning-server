package com.earlylearning.early_learning_server.identity.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.identity.dto.AdminLoginRequest;
import com.earlylearning.early_learning_server.identity.dto.AdminSessionResponse;
import com.earlylearning.early_learning_server.identity.service.AdminLoginService;

/** 管理员登录（契约 adminLogin）。免认证。 */
@RestController
public class AdminAuthController {

    private final AdminLoginService loginService;

    public AdminAuthController(AdminLoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/admin/login")
    public ApiResponse<AdminSessionResponse> login(@RequestBody AdminLoginRequest request) {
        return ApiResponse.ok(loginService.login(request.username(), request.password()));
    }
}
