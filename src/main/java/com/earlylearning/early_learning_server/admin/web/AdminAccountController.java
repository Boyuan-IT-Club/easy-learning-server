package com.earlylearning.early_learning_server.admin.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.admin.AdminAccountService;
import com.earlylearning.early_learning_server.admin.AdminStatus;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyKeys;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageParams;

/** 管理员控制（契约"管理员控制"）。登录免认证，其余要求管理员凭证（由安全链保证）。 */
@RestController
public class AdminAccountController {

    private final AdminAccountService accounts;

    public AdminAccountController(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/admin/login")
    public ApiResponse<AdminSessionResponse> login(@RequestBody AdminLoginRequest request) {
        return ApiResponse.ok(accounts.login(request.username(), request.password()));
    }

    @PostMapping("/admin/accounts")
    public ResponseEntity<String> create(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody CreateAdminAccountRequest request) {
        StoredResponse stored = accounts.create(request.username(), request.password(),
                IdempotencyKeys.require(idempotencyKey));
        return ResponseEntity.status(stored.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(stored.body());
    }

    @GetMapping("/admin/accounts")
    public ApiResponse<AdminAccountPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AdminStatus status) {
        return ApiResponse.ok(accounts.list(PageParams.of(page, pageSize), username, status));
    }

    @PatchMapping("/admin/accounts/{id}")
    public ApiResponse<AdminAccountResponse> update(@PathVariable int id,
                                                    @RequestBody UpdateAdminAccountRequest request) {
        return ApiResponse.ok(accounts.update(id, request.password(), request.status()));
    }
}
