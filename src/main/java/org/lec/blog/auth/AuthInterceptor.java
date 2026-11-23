package org.lec.blog.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.regex.Pattern;
import org.lec.blog.common.Text;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.lec.blog.config.LecBlogProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final Pattern BEARER = Pattern.compile("Bearer +([A-Za-z0-9._~+/-]+=*)", Pattern.CASE_INSENSITIVE);
    private final LecSsoTokenVerifier verifier;
    private final ApplicationEventPublisher events;
    private final boolean mockEnabled;
    private final String orgId;

    public AuthInterceptor(LecSsoTokenVerifier verifier, ApplicationEventPublisher events,
                           LecBlogProperties properties, Environment environment) {
        this.verifier = verifier;
        this.events = events;
        this.orgId = properties.auth().orgId();
        this.mockEnabled = properties.auth().mockEnabled()
                && environment.acceptsProfiles(Profiles.of("(dev | test) & !prod"));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        AuthUser user;
        if (authorization == null && mockEnabled) {
            user = mockUser(request);
        } else {
            var match = BEARER.matcher(authorization == null ? "" : authorization.strip());
            if (!match.matches()) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            user = verifier.verify(match.group(1));
        }
        boolean requiresCaptain = handler instanceof HandlerMethod method
                && (method.hasMethodAnnotation(RequireCaptain.class)
                || method.getBeanType().isAnnotationPresent(RequireCaptain.class));
        if ((!user.isStudio() && !user.isCaptain()) || (requiresCaptain && !user.isCaptain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        request.setAttribute(AuthContext.USER_ATTRIBUTE, user);
        events.publishEvent(user);
        return true;
    }

    private AuthUser mockUser(HttpServletRequest request) {
        String id = header(request, "X-Dev-User-Id");
        if (id.isBlank() || id.length() > 128) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String username = Text.truncate(header(request, "X-Dev-Username"), 128);
        String displayName = Text.truncate(header(request, "X-Dev-Display-Name"), 128);
        return new AuthUser(id, username, displayName.isEmpty() ? username : displayName,
                Text.truncate(header(request, "X-Dev-Avatar"), 512), List.of(orgId), List.of(), true,
                Boolean.parseBoolean(header(request, "X-Dev-Captain")));
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.strip();
    }
}
