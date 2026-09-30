package com.earlylearning.early_learning_server.admin.interfaces.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.admin.domain.AdminLogin;
import com.earlylearning.early_learning_server.admin.domain.AdminView;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理员接口的请求与响应形状。
 *
 * <p>请求 record 覆盖 {@code toString()}：这些对象可能被框架打进调试日志，里面有密码明文。
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record LoginRequest(@JsonProperty("username") String username,
                               @JsonProperty("password") String password) {
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=REDACTED]";
        }
    }

    public record ChangePasswordRequest(@JsonProperty("old_password") String oldPassword,
                                        @JsonProperty("new_password") String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[REDACTED]";
        }
    }

    public record CreateAdminRequest(@JsonProperty("username") String username,
                                     @JsonProperty("initial_password") String initialPassword) {
        @Override
        public String toString() {
            return "CreateAdminRequest[username=" + username + ", initialPassword=REDACTED]";
        }
    }

    public record ResetPasswordRequest(@JsonProperty("new_password") String newPassword) {
        @Override
        public String toString() {
            return "ResetPasswordRequest[REDACTED]";
        }
    }

    public record AdminResponse(@JsonProperty("id") int id,
                                @JsonProperty("username") String username,
                                @JsonProperty("status") String status,
                                @JsonProperty("created_at") Instant createdAt) {

        public static AdminResponse from(AdminView view) {
            return new AdminResponse(view.id(), view.username(), view.status().name(), view.createdAt());
        }
    }

    public record LoginResponse(@JsonProperty("admin_token") String adminToken,
                                @JsonProperty("expires_at") Instant expiresAt,
                                @JsonProperty("admin") AdminResponse admin) {

        public static LoginResponse from(AdminLogin login) {
            return new LoginResponse(login.token().value(), login.token().expiresAt(),
                    AdminResponse.from(login.admin()));
        }

        @Override
        public String toString() {
            return "LoginResponse[adminToken=REDACTED, admin=" + admin + "]";
        }
    }
}
