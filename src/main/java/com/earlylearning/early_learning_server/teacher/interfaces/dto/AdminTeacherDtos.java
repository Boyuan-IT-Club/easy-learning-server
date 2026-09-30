package com.earlylearning.early_learning_server.teacher.interfaces.dto;

import java.time.Instant;
import java.util.List;

import com.earlylearning.early_learning_server.teacher.domain.IssuedRecoveryCode;
import com.earlylearning.early_learning_server.teacher.domain.TeacherPage;
import com.earlylearning.early_learning_server.teacher.domain.TeacherSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 后台教师管理的请求与响应形状。 */
public final class AdminTeacherDtos {

    private AdminTeacherDtos() {
    }

    public record ReasonRequest(@JsonProperty("reason") String reason) {
    }

    public record LicenseBrief(@JsonProperty("id") int id,
                               @JsonProperty("code_hint") String codeHint,
                               @JsonProperty("status") String status,
                               @JsonProperty("remark") String remark) {
    }

    public record TeacherResponse(@JsonProperty("id") int id,
                                  @JsonProperty("username") String username,
                                  @JsonProperty("status") String status,
                                  @JsonProperty("license") LicenseBrief license,
                                  @JsonProperty("device_bound") boolean deviceBound,
                                  @JsonProperty("device_bound_at") Instant deviceBoundAt,
                                  @JsonProperty("last_refresh_at") Instant lastRefreshAt,
                                  @JsonProperty("created_at") Instant createdAt,
                                  @JsonInclude(JsonInclude.Include.NON_NULL)
                                  @JsonProperty("disabled_reason") String disabledReason) {

        /** 列表不返回停用原因，详情才返回。设备号本身不对外。 */
        public static TeacherResponse brief(TeacherSummary summary) {
            return of(summary, null);
        }

        public static TeacherResponse detail(TeacherSummary summary) {
            return of(summary, summary.disabledReason());
        }

        private static TeacherResponse of(TeacherSummary summary, String disabledReason) {
            LicenseBrief license = summary.licenseId() == null ? null : new LicenseBrief(summary.licenseId(),
                    summary.licenseCodeHint(), summary.licenseStatus().name(), summary.licenseRemark());
            return new TeacherResponse(summary.id(), summary.username(), summary.status().display(), license,
                    summary.deviceBound(), summary.deviceBoundAt(), summary.lastRefreshAt(), summary.createdAt(),
                    disabledReason);
        }
    }

    public record TeacherPageResponse(@JsonProperty("items") List<TeacherResponse> items,
                                      @JsonProperty("page") int page,
                                      @JsonProperty("page_size") int pageSize,
                                      @JsonProperty("total") long total) {

        public static TeacherPageResponse from(TeacherPage page) {
            return new TeacherPageResponse(page.items().stream().map(TeacherResponse::brief).toList(),
                    page.page(), page.size(), page.total());
        }
    }

    public record RecoveryCodeResponse(@JsonProperty("recovery_code") String recoveryCode,
                                       @JsonProperty("expires_at") Instant expiresAt) {

        public static RecoveryCodeResponse from(IssuedRecoveryCode issued) {
            return new RecoveryCodeResponse(issued.recoveryCode(), issued.expiresAt());
        }

        @Override
        public String toString() {
            return "RecoveryCodeResponse[REDACTED]";
        }
    }
}
