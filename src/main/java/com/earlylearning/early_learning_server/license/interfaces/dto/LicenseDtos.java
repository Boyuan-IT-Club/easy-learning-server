package com.earlylearning.early_learning_server.license.interfaces.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.earlylearning.early_learning_server.license.domain.LicenseBatch;
import com.earlylearning.early_learning_server.license.domain.LicensePage;
import com.earlylearning.early_learning_server.license.domain.LicenseSummary;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 激活码接口的请求与响应形状。
 *
 * <p>请求体不加 Bean Validation 注解：那类失败回报的字段名是 Java 字段名，而 details.field_path
 * 需要请求体里的 JSON Pointer（如 {@code /license_ids}），所以校验放在服务层。
 */
public final class LicenseDtos {

    private LicenseDtos() {
    }

    public record BatchRequest(@JsonProperty("count") Integer count,
                               @JsonProperty("remark") String remark) {
    }

    public record LookupRequest(@JsonProperty("activation_code") String activationCode) {
    }

    public record RevokeRequest(@JsonProperty("license_ids") List<Integer> licenseIds,
                                @JsonProperty("reason") String reason) {
    }

    public record BatchResponse(@JsonProperty("licenses") List<LicenseBatch.Issued> licenses) {

        public static BatchResponse from(LicenseBatch batch) {
            return new BatchResponse(batch.licenses());
        }
    }

    public record LicenseResponse(@JsonProperty("id") int id,
                                  @JsonProperty("code_hint") String codeHint,
                                  @JsonProperty("status") String status,
                                  @JsonProperty("remark") String remark,
                                  @JsonProperty("user_id") Integer userId,
                                  @JsonProperty("username") String username,
                                  @JsonProperty("created_at") Instant createdAt,
                                  @JsonProperty("activated_at") Instant activatedAt,
                                  @JsonProperty("revoked_at") Instant revokedAt,
                                  @JsonProperty("revoke_reason") String revokeReason) {

        public static LicenseResponse from(LicenseSummary summary) {
            return new LicenseResponse(summary.id(), summary.codeHint(), summary.status().name(), summary.remark(),
                    summary.userId(), summary.username(), summary.createdAt(), summary.activatedAt(),
                    summary.revokedAt(), summary.revokeReason());
        }
    }

    public record LicensePageResponse(@JsonProperty("items") List<LicenseResponse> items,
                                      @JsonProperty("page") int page,
                                      @JsonProperty("page_size") int pageSize,
                                      @JsonProperty("total") long total,
                                      @JsonProperty("counts") Map<String, Long> counts) {

        public static LicensePageResponse from(LicensePage page) {
            Map<String, Long> counts = new java.util.LinkedHashMap<>();
            page.counts().forEach((status, total) -> counts.put(status.name(), total));
            return new LicensePageResponse(page.items().stream().map(LicenseResponse::from).toList(),
                    page.page(), page.size(), page.total(), counts);
        }
    }
}
