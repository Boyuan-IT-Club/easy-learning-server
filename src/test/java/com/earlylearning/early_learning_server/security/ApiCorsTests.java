package com.earlylearning.early_learning_server.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.earlylearning.early_learning_server.security.config.SecurityConfig;
import com.earlylearning.early_learning_server.security.model.TeacherPrincipal;
import com.earlylearning.early_learning_server.security.model.TokenType;
import com.earlylearning.early_learning_server.security.service.BearerAuthenticator;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用实际 SecurityFilterChain；无数据库、OSS 或 AI 调用。
 *
 * <p>探针路径在 /api/ 下，需要教师凭证：这里用一个只认 {@link #PROBE_TOKEN} 的认证实现代替 Redis 与数据库。
 */
@SpringJUnitConfig(ApiCorsTests.WebConfig.class)
@WebAppConfiguration
class ApiCorsTests {
    private static final String WEBVIEW_ORIGIN = "https://localhost";
    private static final String PROBE_TOKEN = "at_cors-probe";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ProbeController.class})
    static class WebConfig {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        BearerAuthenticator probeTeacher() {
            return new BearerAuthenticator() {
                @Override
                public TokenType type() {
                    return TokenType.ACCESS;
                }

                @Override
                public TeacherPrincipal authenticate(String token) {
                    return new TeacherPrincipal(1);
                }
            };
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/cors-probe")
        String success() { return "ok"; }

        @GetMapping("/api/cors-probe/error")
        ResponseEntity<Void> error() { return ResponseEntity.badRequest().build(); }
    }

    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost", "http://localhost:5173", "http://127.0.0.1:5173"})
    void actualResponsesAllowConfiguredClients(String origin) throws Exception {
        mvc.perform(get("/api/cors-probe").header("Origin", origin).header("Authorization", "Bearer " + PROBE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/score", "/api/ai/transcribe", "/api/auth/register"})
    void preflightAllowsJsonMultipartAndIdempotency(String path) throws Exception {
        mvc.perform(options(path).header("Origin", WEBVIEW_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization,Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", WEBVIEW_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Idempotency-Key")));
    }

    @Test
    void errorResponsesRemainReadableByTheWebview() throws Exception {
        mvc.perform(get("/api/cors-probe/error").header("Origin", WEBVIEW_ORIGIN)
                        .header("Authorization", "Bearer " + PROBE_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", WEBVIEW_ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "null"})
    void unlistedOriginsAreRejected(String origin) throws Exception {
        mvc.perform(options("/api/ai/score").header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void unlistedHeadersAreRejected() throws Exception {
        mvc.perform(options("/api/ai/score").header("Origin", WEBVIEW_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Unlisted-Header"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutOriginAndOtherSecurityChainsKeepTheirBehavior() throws Exception {
        mvc.perform(get("/api/cors-probe").header("Authorization", "Bearer " + PROBE_TOKEN)).andExpect(status().isOk());
        mvc.perform(get("/something-else")).andExpect(status().isUnauthorized());
    }
}
