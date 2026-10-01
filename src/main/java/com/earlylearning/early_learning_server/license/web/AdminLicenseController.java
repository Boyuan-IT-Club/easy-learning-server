package com.earlylearning.early_learning_server.license.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.auth.AdminPrincipal;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyKeys;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageParams;
import com.earlylearning.early_learning_server.license.LicenseService;
import com.earlylearning.early_learning_server.license.LicenseStatus;

/** 激活码管理（契约"激活码管理"）。权限：管理员（由安全链保证）。 */
@RestController
public class AdminLicenseController {

    private final LicenseService licenseService;

    public AdminLicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    /** 单个或批量生成。响应含激活码原文，只在首次与短时重放中出现。 */
    @PostMapping("/admin/licenses")
    public ResponseEntity<String> create(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @AuthenticationPrincipal AdminPrincipal admin,
            @RequestBody CreateLicensesRequest request) {
        StoredResponse stored = licenseService.create(request.count(), admin.adminId(),
                IdempotencyKeys.require(idempotencyKey));
        return ResponseEntity.status(stored.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(stored.body());
    }

    @GetMapping("/admin/licenses")
    public ApiResponse<LicensePageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) LicenseStatus status,
            @RequestParam(name = "user_id", required = false) Integer userId) {
        if (userId != null && userId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/parameters/user_id"));
        }
        return ApiResponse.ok(licenseService.list(PageParams.of(page, pageSize), status, userId));
    }

    /** 撤销；已撤销的重复撤销直接返回当前状态。 */
    @PostMapping("/admin/licenses/{id}/revoke")
    public ApiResponse<LicenseResponse> revoke(@PathVariable int id) {
        return ApiResponse.ok(licenseService.revoke(id));
    }
}
