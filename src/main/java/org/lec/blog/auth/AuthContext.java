package org.lec.blog.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {
    private AuthContext() {}

    public static final String USER_ATTRIBUTE = "org.lec.blog.auth.user";

    public static AuthUser user(HttpServletRequest request) {
        return (AuthUser) request.getAttribute(USER_ATTRIBUTE);
    }
}
