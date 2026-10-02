package com.earlylearning.early_learning_server.security.config;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.security.filter.BearerTokenFilter;
import com.earlylearning.early_learning_server.security.filter.SecurityErrorWriter;
import com.earlylearning.early_learning_server.security.model.AdminPrincipal;
import com.earlylearning.early_learning_server.security.model.TeacherPrincipal;
import com.earlylearning.early_learning_server.security.service.BearerAuthenticator;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 安全链：一条无状态链覆盖全部路径，角色规则逐条取自契约各操作的 {@code security} 声明。
 *
 * <pre>
 *   免登录      POST /admin/login、POST /api/auth/register、POST /api/auth/refresh
 *   管理员或教师 /api/files/**、/api/ai/rubrics、/api/assessment-materials/**、/api/courses/**、
 *               /api/dictionary、/api/grammars
 *   仅教师      其余 /api/**（AI 转写、评分、任务查询）
 *   仅管理员    其余 /admin/**
 *   其他路径    拒绝
 * </pre>
 *
 * <p>调用方都用 Authorization 头携带 Token，没有 Cookie 会话，所以关闭 CSRF、会话、表单登录与 Basic。
 *
 * <p>CORS：平板 WebView（{@code https://localhost}）与本地联调（浏览器模拟器、Apifox 浏览器模式）需要跨域放行。
 * 来源由 {@code app.cors.allowed-origin-patterns} 配置；未配置时只放行 WebView 与本机开发地址，
 * application.yaml 在开发环境显式放行全部来源，生产部署应收紧为具体域名。凭据不跨域，令牌走 Authorization 头。
 * 不使用 Spring 默认生成的用户（AGENTS.md 第 8 节）。
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class SecurityConfig {

    private static final String TEACHER = TeacherPrincipal.ROLE;
    private static final String ADMIN = AdminPrincipal.ROLE;

    /** 契约中同时接受 AdminBearer 与 TeacherBearer 的操作。 */
    static final String[] SHARED = {
            "/api/files/**", "/api/ai/rubrics", "/api/assessment-materials/**", "/api/courses/**",
            "/api/dictionary", "/api/grammars",
    };

    /**
     * 声明一个拒绝一切的 AuthenticationManager：本项目不走用户名密码认证链，
     * 有了它，Boot 就不会再自动生成那个默认用户与随机密码。
     */
    @Bean
    AuthenticationManager noUsernamePasswordAuthentication() {
        return authentication -> {
            throw new ProviderNotFoundException("不支持用户名密码认证");
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin-patterns:https://localhost,http://localhost:*,http://127.0.0.1:*}")
            List<String> allowedOriginPatterns) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http,
                                 List<BearerAuthenticator> authenticators,
                                 ObjectMapper objectMapper) throws Exception {
        SecurityErrorWriter errorWriter = new SecurityErrorWriter(objectMapper);
        RequestMatcher publicEndpoints = publicEndpoints();

        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new BearerTokenFilter(authenticators, publicEndpoints, errorWriter),
                        AnonymousAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) -> {
                            log.warn("未携带凭证访问受保护接口 {} {}", request.getMethod(), request.getRequestURI());
                            errorWriter.write(response, ErrorCode.TOKEN_MISSING);
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            log.warn("身份与接口不匹配 {} {}", request.getMethod(), request.getRequestURI());
                            errorWriter.write(response, ErrorCode.AUTH_ROLE_MISMATCH);
                        }))
                .authorizeHttpRequests(auth -> auth
                        // 异常转发到 /error 时沿用原请求已经通过的判定，避免把真实错误掩盖成 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .requestMatchers(SHARED).hasAnyRole(TEACHER, ADMIN)
                        .requestMatchers("/api/**").hasRole(TEACHER)
                        .requestMatchers("/admin/**").hasRole(ADMIN)
                        .anyRequest().denyAll());
        return http.build();
    }

    static RequestMatcher publicEndpoints() {
        PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                path.matcher(HttpMethod.POST, "/admin/login"),
                path.matcher(HttpMethod.POST, "/api/auth/register"),
                path.matcher(HttpMethod.POST, "/api/auth/refresh"));
    }
}
