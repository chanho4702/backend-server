package com.platform.boardservice.security;

import com.platform.boardservice.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class AccessGuardTest {

    AccessGuard guard = new AccessGuard();

    @Test
    void readsUserIdAndName() {
        var auth = TestAuth.user(42L, "Alice");
        assertThat(guard.currentUserId(auth)).isEqualTo(42L);
        assertThat(guard.currentUserName(auth)).isEqualTo("Alice");
        assertThat(guard.isAdmin(auth)).isFalse();
    }

    @Test
    void ownerPasses() {
        assertThatCode(() -> guard.requireOwnerOrAdmin(42L, TestAuth.user(42L, "Alice")))
                .doesNotThrowAnyException();
    }

    @Test
    void adminPassesForOthersResource() {
        assertThatCode(() -> guard.requireOwnerOrAdmin(99L, TestAuth.admin(1L, "Root")))
                .doesNotThrowAnyException();
    }

    @Test
    void otherUserRejected() {
        assertThatThrownBy(() -> guard.requireOwnerOrAdmin(99L, TestAuth.user(42L, "Alice")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonNumericSubjectRejectedAsAccessDeniedNotServerError() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("not-a-number")
                .claim("roles", List.of("USER"))
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThatThrownBy(() -> guard.requireOwnerOrAdmin(42L, auth))
                .isInstanceOf(AccessDeniedException.class);
    }
}
