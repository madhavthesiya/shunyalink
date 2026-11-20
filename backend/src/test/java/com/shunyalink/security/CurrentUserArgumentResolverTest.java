package com.shunyalink.security;

import com.shunyalink.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @SuppressWarnings("unused")
    private void endpoint(@CurrentUser Long required, @CurrentUser(required = false) Long optional, Long plain) {
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = CurrentUserArgumentResolverTest.class.getDeclaredMethod("endpoint", Long.class, Long.class, Long.class);
        return new MethodParameter(method, index);
    }

    private static void signedIn(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theSignedInUsersId_isInjected_whateverNumberTypeTheFilterUsed() throws Exception {
        signedIn(7L);
        assertThat(resolver.resolveArgument(parameter(0), null, null, null)).isEqualTo(7L);

        signedIn(8); // an Integer principal is still accepted, as the old copied code did
        assertThat(resolver.resolveArgument(parameter(0), null, null, null)).isEqualTo(8L);
    }

    @Test
    void withoutASignedInUser_aRequiredParameter_is401() throws Exception {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, null, null))
                .isInstanceOf(UnauthorizedException.class);

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void withoutASignedInUser_anOptionalParameter_isNull() throws Exception {
        assertThat(resolver.resolveArgument(parameter(1), null, null, null)).isNull();
    }

    @Test
    void onlyAnnotatedLongParameters_areResolved() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    }
}
