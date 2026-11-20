package com.shunyalink.security;

import com.shunyalink.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CurrentUser} parameters from the security context set by {@link JwtAuthenticationFilter}. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class) && parameter.getParameterType() == Long.class;
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Long userId = userId(SecurityContextHolder.getContext().getAuthentication());
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        if (userId == null && annotation != null && annotation.required()) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }

    /** The JWT filter sets the user's numeric ID as the principal. Anyone else (anonymous, none) has no ID. */
    static Long userId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof Number id ? id.longValue() : null;
    }
}
