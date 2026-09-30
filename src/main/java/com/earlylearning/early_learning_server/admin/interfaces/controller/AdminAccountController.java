package com.earlylearning.early_learning_server.admin.interfaces.controller;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.admin.application.AdminAccountService;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminDtos.AdminResponse;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminDtos.CreateAdminRequest;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminDtos.ResetPasswordRequest;
import com.earlylearning.early_learning_server.auth.domain.AdminPrincipal;
import com.earlylearning.early_learning_server.common.web.ApiResponse;

/** 管理员维护。权限：管理员（由安全链保证）。 */
@RestController
@Validated
public class AdminAccountController {

    private final AdminAccountService accountService;

    public AdminAccountController(AdminAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/admin/admins")
    public ApiResponse<List<AdminResponse>> list() {
        return ApiResponse.ok(accountService.list().stream().map(AdminResponse::from).toList());
    }

    @PostMapping("/admin/admins")
    public ResponseEntity<ApiResponse<AdminResponse>> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestBody CreateAdminRequest request,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(AdminResponse.from(
                accountService.create(request.username(), request.initialPassword(), admin.adminId(),
                        idempotencyKey))));
    }

    @PostMapping("/admin/admins/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable int id, @AuthenticationPrincipal AdminPrincipal admin) {
        accountService.disable(id, admin.adminId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/admins/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable int id, @AuthenticationPrincipal AdminPrincipal admin) {
        accountService.enable(id, admin.adminId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/admins/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable int id,
                                           @RequestBody ResetPasswordRequest request,
                                           @AuthenticationPrincipal AdminPrincipal admin) {
        accountService.resetPassword(id, request.newPassword(), admin.adminId());
        return ApiResponse.ok(null);
    }
}
