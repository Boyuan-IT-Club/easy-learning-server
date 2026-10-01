package com.earlylearning.early_learning_server.auth.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.auth.RefreshService;
import com.earlylearning.early_learning_server.auth.RegistrationService;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyKeys;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;

/** 教师注册与刷新（契约"用户与鉴权"）。两个接口都免认证。 */
@RestController
public class AuthController {

    private final RegistrationService registration;
    private final RefreshService refresh;

    public AuthController(RegistrationService registration, RefreshService refresh) {
        this.registration = registration;
        this.refresh = refresh;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<String> register(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody RegisterRequest request) {
        return json(registration.register(request.activationCode(), request.username(),
                IdempotencyKeys.require(idempotencyKey)));
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<String> refresh(
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody RefreshRequest request) {
        return json(refresh.refresh(request.refreshToken(), IdempotencyKeys.require(idempotencyKey)));
    }

    private static ResponseEntity<String> json(StoredResponse stored) {
        return ResponseEntity.status(stored.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(stored.body());
    }
}
