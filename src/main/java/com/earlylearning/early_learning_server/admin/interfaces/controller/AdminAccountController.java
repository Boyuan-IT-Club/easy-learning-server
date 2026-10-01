package com.earlylearning.early_learning_server.admin.interfaces.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.admin.application.AdminAccountService;
import com.earlylearning.early_learning_server.admin.application.AdminLoginService;
import com.earlylearning.early_learning_server.admin.domain.AdminStatus;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminAccountResponse;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminLoginRequest;
import com.earlylearning.early_learning_server.admin.interfaces.dto.AdminSessionResponse;
import com.earlylearning.early_learning_server.admin.interfaces.dto.CreateAdminAccountRequest;
import com.earlylearning.early_learning_server.admin.interfaces.dto.UpdateAdminAccountRequest;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyKeys;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageResponse;

/** 管理员控制（契约"管理员"）。登录免认证，其余要求管理员凭证（由安全链保证）。 */
@RestController
public class AdminAccountController {

    private final AdminLoginService loginService;
    private final AdminAccountService accounts;

    public AdminAccountController(AdminLoginService loginService, AdminAccountService accounts) {
        this.loginService = loginService;
        this.accounts = accounts;
    }

    @PostMapping("/admin/login")
    public ApiResponse<AdminSessionResponse> login(@RequestBody AdminLoginRequest request) {
        return ApiResponse.ok(AdminSessionResponse.from(loginService.login(request.username(), request.password())));
    }

    @PostMapping("/admin/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminAccountResponse> create(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody CreateAdminAccountRequest request) {
        return ApiResponse.ok(AdminAccountResponse.from(accounts.create(request.username(), request.password(),
                IdempotencyKeys.require(idempotencyKey))));
    }

    @GetMapping("/admin/accounts")
    public ApiResponse<PageResponse<AdminAccountResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AdminStatus status) {
        return ApiResponse.ok(PageResponse.from(
                accounts.list(PageQuery.of(page, pageSize), username, status), AdminAccountResponse::from));
    }

    @PatchMapping("/admin/accounts/{id}")
    public ApiResponse<AdminAccountResponse> update(@PathVariable int id,
                                                    @RequestBody UpdateAdminAccountRequest request) {
        return ApiResponse.ok(AdminAccountResponse.from(accounts.update(id, request.password(), request.status())));
    }
}
