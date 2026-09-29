package com.earlylearning.early_learning_server.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全链：客户端 API 与浏览器侧分开切。
 *
 * <p>为什么必须分开：契约里的 {@code /api/} 调用方是平板等非浏览器客户端——
 * {@code api/}…openapi.json 里没有任何会话或 CSRF 步骤。而 Spring Security 默认开启 CSRF，
 * {@code CsrfFilter} 会在认证之前拒掉 POST，再由 Basic 入口点回 401：
 * 结果是所有 {@code /api/} 的 POST 都调不通（GET 却正常，因为 CSRF 只管不安全方法）。
 *
 * <p>所以：
 * <ul>
 *   <li>{@code /api/} —— 无状态、免 CSRF、暂不鉴权（鉴权由凭证层承担，待 01_账号与鉴权 落地）；</li>
 *   <li>其余（登录页与将来的 {@code /admin/}）—— 保留会话与 CSRF，行为与默认链一致。</li>
 * </ul>
 *
 * <p>注意：自己定义 {@code SecurityFilterChain} 会让 Boot 的默认安全配置退让，
 * 所以浏览器侧这条必须显式写出 formLogin 与 httpBasic，否则会连登录页一起丢掉。
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain statelessApiChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**", "/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain browserChain(HttpSecurity http) throws Exception {
        // CSRF 保持默认开启：浏览器侧的表单与将来的管理端需要它
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
