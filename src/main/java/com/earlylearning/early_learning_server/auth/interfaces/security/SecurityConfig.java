package com.earlylearning.early_learning_server.auth.interfaces.security;

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

import com.earlylearning.early_learning_server.auth.domain.AdminPrincipal;
import com.earlylearning.early_learning_server.auth.domain.BearerAuthenticator;
import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.auth.domain.TeacherPrincipal;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

import tools.jackson.databind.ObjectMapper;

/**
 * 安全链：一条无状态链覆盖全部路径，按路径分配角色。
 *
 * <p>调用方都是非浏览器客户端或以 Authorization 头携带 Token 的管理端，没有 Cookie 会话，
 * 所以关闭 CSRF、会话、表单登录与 Basic；不使用 Spring 默认生成的用户（见 AGENTS.md 第 8 节）。
 *
 * <pre>
 *   免登录   POST /api/auth/licenses/verify、/register、/refresh、/recover；POST /admin/auth/login
 *   双角色   /api/files/**、/api/ai/rubrics、/api/assessment-materials/**（按契约 security 声明）
 *   TEACHER  其余 /api/**
 *   ADMIN    其余 /admin/**
 *   其他路径 拒绝
 * </pre>
 *
 * <p>CORS：来源由 {@code app.cors.allowed-origin-patterns} 配置，开发默认放行全部来源
 * （凭据不跨域，令牌走 Authorization 头）；生产部署应收紧为具体域名。
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String TEACHER = TeacherPrincipal.ROLE;
    private static final String ADMIN = AdminPrincipal.ROLE;

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin-patterns:*}")
            List<String> allowedOriginPatterns) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", DeviceId.HEADER));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

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
                .addFilterBefore(new BearerTokenAuthenticationFilter(authenticators, publicEndpoints, errorWriter),
                        AnonymousAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) ->
                                errorWriter.write(response, ErrorCode.TOKEN_MISSING))
                        .accessDeniedHandler((request, response, e) ->
                                errorWriter.write(response, ErrorCode.AUTH_ROLE_MISMATCH)))
                .authorizeHttpRequests(auth -> auth
                        // 异常转发到 /error 时沿用原请求已经通过的判定，避免把真实错误掩盖成 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .requestMatchers("/api/files/**", "/api/ai/rubrics", "/api/assessment-materials/**")
                        .hasAnyRole(TEACHER, ADMIN)
                        .requestMatchers("/api/**").hasRole(TEACHER)
                        .requestMatchers("/admin/**").hasRole(ADMIN)
                        .anyRequest().denyAll());
        return http.build();
    }

    private static RequestMatcher publicEndpoints() {
        PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                path.matcher(HttpMethod.POST, "/api/auth/licenses/verify"),
                path.matcher(HttpMethod.POST, "/api/auth/register"),
                path.matcher(HttpMethod.POST, "/api/auth/refresh"),
                path.matcher(HttpMethod.POST, "/api/auth/recover"),
                path.matcher(HttpMethod.POST, "/admin/auth/login"));
    }
}
