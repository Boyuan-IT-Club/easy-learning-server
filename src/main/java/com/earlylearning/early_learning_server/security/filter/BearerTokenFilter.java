package com.earlylearning.early_learning_server.security.filter;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.security.model.AuthPrincipal;
import com.earlylearning.early_learning_server.security.model.TokenType;
import com.earlylearning.early_learning_server.security.service.BearerAuthenticator;

/**
 * 读取 {@code Authorization: Bearer <token>}，按前缀交给对应的 {@link BearerAuthenticator}。
 *
 * <ul>
 *   <li>没有 Bearer：不设身份，交给授权规则（免登录接口放行，其余由入口点回 401 TOKEN_MISSING）；</li>
 *   <li>前缀无人认领（包括把 refresh_token 当 access 用）：401 TOKEN_INVALID；</li>
 *   <li>认证失败：直接写出具体错误码（TOKEN_EXPIRED、ACCOUNT_DISABLED、LICENSE_REVOKED…），
 *       因为只有这里知道失败的真实原因，入口点只知道"没认证"。</li>
 * </ul>
 *
 * <p>免登录接口（管理员登录、注册、刷新）<b>完全跳过</b>这里：平板在 access 过期时调刷新接口，
 * 若还带着旧 Bearer，不能因为它过期就把刷新本身拒掉。
 *
 * <p>不是 Spring Bean：否则 Boot 会把它再注册成一个普通 Servlet 过滤器，每个请求执行两遍。
 */
public final class BearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final Map<TokenType, BearerAuthenticator> authenticators = new EnumMap<>(TokenType.class);
    private final RequestMatcher publicEndpoints;
    private final SecurityErrorWriter errorWriter;

    public BearerTokenFilter(List<BearerAuthenticator> authenticators,
                             RequestMatcher publicEndpoints,
                             SecurityErrorWriter errorWriter) {
        for (BearerAuthenticator authenticator : authenticators) {
            if (this.authenticators.put(authenticator.type(), authenticator) != null) {
                throw new IllegalStateException("同一种 Token 有多个认证实现: " + authenticator.type());
            }
        }
        this.publicEndpoints = publicEndpoints;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        Optional<BearerAuthenticator> authenticator = TokenType.of(token).map(authenticators::get);
        if (authenticator.isEmpty()) {
            errorWriter.write(response, ErrorCode.TOKEN_INVALID);
            return;
        }

        AuthPrincipal principal;
        try {
            principal = authenticator.get().authenticate(token);
        } catch (BusinessException e) {
            errorWriter.write(response, e);
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PrincipalAuthentication(principal));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
