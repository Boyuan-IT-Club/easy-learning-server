package com.earlylearning.early_learning_server.identity.controller;

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

import com.earlylearning.early_learning_server.common.idempotency.IdempotencyKeys;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.entity.AdminStatus;
import com.earlylearning.early_learning_server.identity.dto.AdminAccountResponse;
import com.earlylearning.early_learning_server.identity.dto.CreateAdminAccountRequest;
import com.earlylearning.early_learning_server.identity.dto.UpdateAdminAccountRequest;
import com.earlylearning.early_learning_server.identity.service.AdminAccountService;

/** 管理员账号维护（契约 createAdminAccount、listAdminAccounts、updateAdminAccount）。权限：管理员。 */
@RestController
public class AdminAccountController {

    private final AdminAccountService accounts;

    public AdminAccountController(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/admin/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminAccountResponse> create(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody CreateAdminAccountRequest request) {
        return ApiResponse.ok(accounts.create(request.username(), request.password(),
                IdempotencyKeys.require(idempotencyKey)));
    }

    @GetMapping("/admin/accounts")
    public ApiResponse<PageResponse<AdminAccountResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AdminStatus status) {
        return ApiResponse.ok(accounts.list(PageQuery.of(page, pageSize), username, status));
    }

    @PatchMapping("/admin/accounts/{id}")
    public ApiResponse<AdminAccountResponse> update(@PathVariable int id,
                                                    @RequestBody UpdateAdminAccountRequest request) {
        return ApiResponse.ok(accounts.update(id, request.password(), request.status()));
    }
}
