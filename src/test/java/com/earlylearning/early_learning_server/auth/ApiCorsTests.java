package com.earlylearning.early_learning_server.auth;

import com.earlylearning.early_learning_server.auth.domain.BearerAuthenticator;
import com.earlylearning.early_learning_server.auth.domain.TeacherPrincipal;
import com.earlylearning.early_learning_server.auth.domain.TokenType;
import com.earlylearning.early_learning_server.auth.interfaces.security.SecurityConfig;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 走真实 CORS 与 Bearer 过滤器，只替换凭证查询，验证 WebView 能读取成功和鉴权失败的响应。 */
@SpringJUnitConfig(ApiCorsTests.WebConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = "app.cors.allowed-origin-patterns=https://localhost,http://localhost:5173,http://127.0.0.1:5173")
class ApiCorsTests {

    private static final String WEBVIEW_ORIGIN = "https://localhost";
    private static final String ACCESS_TOKEN = "at_cors_test";
    private static final String DEVICE_ID = "33333333-3333-4333-8333-333333333333";

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class WebConfig {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        BearerAuthenticator teacherAuthenticator() {
            BearerAuthenticator authenticator = mock(BearerAuthenticator.class);
            when(authenticator.type()).thenReturn(TokenType.ACCESS);
            return authenticator;
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/cors-probe")
        String success() {
            return "ok";
        }

        @GetMapping("/api/cors-probe/error")
        ResponseEntity<Void> error() {
            return ResponseEntity.badRequest().build();
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BearerAuthenticator teacherAuthenticator;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(teacherAuthenticator);
        when(teacherAuthenticator.authenticate(ACCESS_TOKEN, DEVICE_ID))
                .thenReturn(new TeacherPrincipal(1, DEVICE_ID));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost", "http://localhost:5173", "http://127.0.0.1:5173"})
    void authenticatedResponsesAllowConfiguredClients(String origin) throws Exception {
        mvc.perform(get("/api/cors-probe").header("Origin", origin)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        verify(teacherAuthenticator).authenticate(ACCESS_TOKEN, DEVICE_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/score", "/api/ai/transcribe", "/api/auth/register", "/admin/assessment-materials"})
    void preflightAllowsAllMobileRequestHeadersWithoutAuthentication(String path) throws Exception {
        mvc.perform(options(path).header("Origin", WEBVIEW_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization,Idempotency-Key,X-Device-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", WEBVIEW_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Idempotency-Key")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Device-Id")));
        verifyNoInteractions(teacherAuthenticator);
    }

    @Test
    void anonymousRequestReturnsReadableTokenMissingResponse() throws Exception {
        mvc.perform(get("/api/cors-probe").header("Origin", WEBVIEW_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_MISSING"))
                .andExpect(header().string("Access-Control-Allow-Origin", WEBVIEW_ORIGIN));
        verifyNoInteractions(teacherAuthenticator);
    }

    @Test
    void expiredTokenReturnsReadableErrorInsteadOfCorsFailure() throws Exception {
        when(teacherAuthenticator.authenticate(ACCESS_TOKEN, DEVICE_ID))
                .thenThrow(new BusinessException(ErrorCode.TOKEN_EXPIRED));
        mvc.perform(get("/api/cors-probe").header("Origin", WEBVIEW_ORIGIN)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
                .andExpect(header().string("Access-Control-Allow-Origin", WEBVIEW_ORIGIN));
    }

    @Test
    void wrongRoleReturnsReadableForbiddenResponse() throws Exception {
        mvc.perform(get("/admin/files").header("Origin", WEBVIEW_ORIGIN)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ROLE_MISMATCH"))
                .andExpect(header().string("Access-Control-Allow-Origin", WEBVIEW_ORIGIN));
    }

    @Test
    void controllerErrorsRemainReadableByTheWebview() throws Exception {
        mvc.perform(get("/api/cors-probe/error").header("Origin", WEBVIEW_ORIGIN)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", WEBVIEW_ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "null"})
    void originsOutsideTheConfiguredAllowlistAreRejected(String origin) throws Exception {
        mvc.perform(options("/api/ai/score").header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(teacherAuthenticator);
    }

    @Test
    void unlistedHeadersAreRejected() throws Exception {
        mvc.perform(options("/api/ai/score").header("Origin", WEBVIEW_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Unlisted-Header"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(teacherAuthenticator);
    }

    @Test
    void nativeRequestsWithoutOriginStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/cors-probe"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cors-probe").header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
