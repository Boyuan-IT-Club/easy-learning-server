package com.earlylearning.early_learning_server.admin.interfaces.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.admin.application.AdminSessionService;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminDtos.AdminResponse;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminDtos.ChangePasswordRequest;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminDtos.LoginRequest;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminDtos.LoginResponse;
import com.earlylearning.early_learning_server.auth.domain.AdminPrincipal;
import com.earlylearning.early_learning_server.common.web.ApiResponse;

/** 管理员登录与会话。登录免鉴权，其余需要管理员 Token。 */
@RestController
public class AdminAuthController {

    private final AdminSessionService sessionService;

    public AdminAuthController(AdminSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/admin/auth/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(LoginResponse.from(sessionService.login(request.username(), request.password())));
    }

    @PostMapping("/admin/auth/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AdminPrincipal admin) {
        sessionService.logout(admin);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/auth/me")
    public ApiResponse<AdminResponse> me(@AuthenticationPrincipal AdminPrincipal admin) {
        return ApiResponse.ok(AdminResponse.from(sessionService.me(admin)));
    }

    @PostMapping("/admin/auth/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request,
                                            @AuthenticationPrincipal AdminPrincipal admin) {
        sessionService.changePassword(admin, request.oldPassword(), request.newPassword());
        return ApiResponse.ok(null);
    }
}
