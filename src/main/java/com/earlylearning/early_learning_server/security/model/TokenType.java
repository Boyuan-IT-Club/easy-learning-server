package com.earlylearning.early_learning_server.security.model;

import java.util.Optional;

/**
 * Token 种类，由前缀区分。契约只要求是 Bearer 字符串；前缀让服务端一眼看出拿错了凭证
 * （例如把 refresh_token 当 access 用），也便于在日志与泄露扫描中识别。
 */
public enum TokenType {

    /** 教师访问凭证。 */
    ACCESS("at_"),
    /** 教师刷新凭证。 */
    REFRESH("rt_"),
    /** 管理员凭证。 */
    ADMIN("adt_");

    private final String prefix;

    TokenType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public static Optional<TokenType> of(String token) {
        if (token == null) {
            return Optional.empty();
        }
        for (TokenType type : values()) {
            if (token.startsWith(type.prefix)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
