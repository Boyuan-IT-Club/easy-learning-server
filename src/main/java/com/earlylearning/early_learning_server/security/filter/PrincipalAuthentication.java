package com.earlylearning.early_learning_server.security.filter;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.earlylearning.early_learning_server.security.model.AuthPrincipal;


/**
 * 放进 SecurityContext 的认证结果。
 *
 * <p>principal 是 {@link AuthPrincipal}，Controller 用 {@code @AuthenticationPrincipal TeacherPrincipal} 取。
 * 不保存 Token 本身（credentials 为 null），避免它随上下文被打印或序列化。
 */
final class PrincipalAuthentication extends AbstractAuthenticationToken {

    private final AuthPrincipal principal;

    PrincipalAuthentication(AuthPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
