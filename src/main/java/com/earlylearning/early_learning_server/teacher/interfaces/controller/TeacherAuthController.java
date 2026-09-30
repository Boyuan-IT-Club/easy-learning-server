package com.earlylearning.early_learning_server.teacher.interfaces.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.auth.domain.TeacherPrincipal;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.teacher.application.RecoveryService;
import com.earlylearning.early_learning_server.teacher.application.RegistrationService;
import com.earlylearning.early_learning_server.teacher.application.TeacherSessionService;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.TeacherAuthDtos.MeResponse;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.TeacherAuthDtos.RecoverRequest;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.TeacherAuthDtos.RefreshRequest;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.TeacherAuthDtos.RegisterRequest;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.TeacherAuthDtos.SessionResponse;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.TeacherAuthDtos.TokenResponse;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.TeacherAuthDtos.VerifyLicenseRequest;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.TeacherAuthDtos.VerifyLicenseResponse;

/**
 * 平板端的账号接口。除 {@code /me} 外都免登录（安全链里放行），各自做限流。
 */
@RestController
@Validated
public class TeacherAuthController {

    private final RegistrationService registrationService;
    private final TeacherSessionService sessionService;
    private final RecoveryService recoveryService;

    public TeacherAuthController(RegistrationService registrationService,
                                 TeacherSessionService sessionService,
                                 RecoveryService recoveryService) {
        this.registrationService = registrationService;
        this.sessionService = sessionService;
        this.recoveryService = recoveryService;
    }

    @PostMapping("/api/auth/licenses/verify")
    public ApiResponse<VerifyLicenseResponse> verifyLicense(@RequestBody VerifyLicenseRequest request) {
        registrationService.verifyLicense(request.activationCode());
        return ApiResponse.ok(new VerifyLicenseResponse(true));
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<ApiResponse<SessionResponse>> register(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader(name = DeviceId.HEADER, required = false) String deviceId,
            @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(SessionResponse.from(
                registrationService.register(request.activationCode(), request.username(), deviceId,
                        idempotencyKey))));
    }

    @PostMapping("/api/auth/refresh")
    public ApiResponse<TokenResponse> refresh(
            @RequestHeader(name = DeviceId.HEADER, required = false) String deviceId,
            @RequestBody RefreshRequest request) {
        return ApiResponse.ok(TokenResponse.from(sessionService.refresh(request.refreshToken(), deviceId)));
    }

    @GetMapping("/api/auth/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal TeacherPrincipal teacher) {
        return ApiResponse.ok(MeResponse.from(sessionService.me(teacher)));
    }

    @PostMapping("/api/auth/recover")
    public ApiResponse<SessionResponse> recover(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader(name = DeviceId.HEADER, required = false) String deviceId,
            @RequestBody RecoverRequest request) {
        return ApiResponse.ok(SessionResponse.from(recoveryService.recover(request.username(),
                request.recoveryCode(), deviceId, idempotencyKey)));
    }
}
