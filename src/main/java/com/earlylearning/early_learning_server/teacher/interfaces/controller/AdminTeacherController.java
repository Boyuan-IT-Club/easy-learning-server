package com.earlylearning.early_learning_server.teacher.interfaces.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.auth.domain.AdminPrincipal;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.teacher.application.TeacherAdminService;
import com.earlylearning.early_learning_server.teacher.domain.TeacherStatus;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.AdminTeacherDtos.ReasonRequest;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.AdminTeacherDtos.RecoveryCodeResponse;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.AdminTeacherDtos.TeacherPageResponse;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.AdminTeacherDtos.TeacherResponse;

/** 后台教师管理。权限：管理员（由安全链保证）。 */
@RestController
@Validated
public class AdminTeacherController {

    private final TeacherAdminService adminService;

    public AdminTeacherController(TeacherAdminService adminService) {
        this.adminService = adminService;
    }

    /** @param status ACTIVE / DISABLED */
    @GetMapping("/admin/teachers")
    public ApiResponse<TeacherPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(TeacherPageResponse.from(
                adminService.page(parseStatus(status), keyword, page, pageSize)));
    }

    @GetMapping("/admin/teachers/{id}")
    public ApiResponse<TeacherResponse> detail(@PathVariable int id) {
        return ApiResponse.ok(TeacherResponse.detail(adminService.detail(id)));
    }

    @PostMapping("/admin/teachers/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable int id,
                                     @RequestBody ReasonRequest request,
                                     @AuthenticationPrincipal AdminPrincipal admin) {
        adminService.disable(id, request.reason(), admin.adminId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/teachers/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable int id, @AuthenticationPrincipal AdminPrincipal admin) {
        adminService.enable(id, admin.adminId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/teachers/{id}/unbind-device")
    public ApiResponse<Void> unbindDevice(@PathVariable int id,
                                          @RequestBody ReasonRequest request,
                                          @AuthenticationPrincipal AdminPrincipal admin) {
        adminService.unbindDevice(id, request.reason(), admin.adminId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/teachers/{id}/recovery-code")
    public ResponseEntity<ApiResponse<RecoveryCodeResponse>> issueRecoveryCode(
            @PathVariable int id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(RecoveryCodeResponse.from(
                adminService.issueRecoveryCode(id, admin.adminId(), idempotencyKey))));
    }

    private static TeacherStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case "ACTIVE" -> TeacherStatus.ENABLED;
            case "DISABLED" -> TeacherStatus.DISABLED;
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/status"));
        };
    }
}
