package com.earlylearning.early_learning_server.license.interfaces.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.auth.domain.AdminPrincipal;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.license.application.LicenseService;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;
import com.earlylearning.early_learning_server.license.interfaces.dto.LicenseDtos.BatchRequest;
import com.earlylearning.early_learning_server.license.interfaces.dto.LicenseDtos.BatchResponse;
import com.earlylearning.early_learning_server.license.interfaces.dto.LicenseDtos.LicensePageResponse;
import com.earlylearning.early_learning_server.license.interfaces.dto.LicenseDtos.LicenseResponse;
import com.earlylearning.early_learning_server.license.interfaces.dto.LicenseDtos.LookupRequest;
import com.earlylearning.early_learning_server.license.interfaces.dto.LicenseDtos.RevokeRequest;

/** 激活码的管理端接口。权限：管理员（由安全链保证）。 */
@RestController
@Validated
public class AdminLicenseController {

    private final LicenseService licenseService;

    public AdminLicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @PostMapping("/admin/licenses/batch")
    public ResponseEntity<ApiResponse<BatchResponse>> batch(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestBody BatchRequest request,
            @AuthenticationPrincipal AdminPrincipal admin) {
        int count = request.count() == null ? 0 : request.count();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(BatchResponse.from(
                licenseService.issueBatch(count, request.remark(), admin.adminId(), idempotencyKey))));
    }

    @GetMapping("/admin/licenses")
    public ApiResponse<LicensePageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) LicenseStatus status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(LicensePageResponse.from(licenseService.page(status, keyword, page, pageSize)));
    }

    @PostMapping("/admin/licenses/lookup")
    public ApiResponse<LicenseResponse> lookup(@RequestBody LookupRequest request) {
        return ApiResponse.ok(LicenseResponse.from(licenseService.lookup(request.activationCode())));
    }

    @PostMapping("/admin/licenses/revoke")
    public ApiResponse<Void> revoke(@RequestBody RevokeRequest request,
                                    @AuthenticationPrincipal AdminPrincipal admin) {
        licenseService.revoke(request.licenseIds(), request.reason(), admin.adminId());
        return ApiResponse.ok(null);
    }
}
