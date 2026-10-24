package com.platform.boardservice.security;

import com.platform.boardservice.TestAuth;
import com.platform.boardservice.org.FakeOrgClient;
import com.platform.common.error.ServiceUnavailableException;
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

    FakeOrgClient org = new FakeOrgClient();
    AccessGuard guard = new AccessGuard(org);

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
    void orgGlobalAdminPassesForOthersResource() {
        org.grantAdmin(1L);
        assertThatCode(() -> guard.requireOwnerOrAdmin(99L, TestAuth.user(1L, "Root")))
                .doesNotThrowAnyException();
    }

    /** KC realm ROLE_ADMIN은 관리자 판정에 쓰지 않는다 — org 전역 관리자가 유일한 기준(M1) */
    @Test
    void realmRoleAdminAloneIsNotAdmin() {
        assertThat(guard.isAdmin(TestAuth.admin(1L, "RealmOnly"))).isFalse();
        assertThatThrownBy(() -> guard.requireOwnerOrAdmin(99L, TestAuth.admin(1L, "RealmOnly")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerDoesNotAskOrg() {
        guard.requireOwnerOrAdmin(42L, TestAuth.user(42L, "Alice"));
        assertThat(org.adminCalls()).isEmpty();
    }

    /** org 불능은 403(권한 없음)이 아니라 503 — 장애를 "관리자가 아니다"로 말하지 않는다 */
    @Test
    void orgUnavailableIsServiceUnavailableNotForbidden() {
        org.setUnavailable(true);
        assertThatThrownBy(() -> guard.requireOwnerOrAdmin(99L, TestAuth.user(1L, "Root")))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    /** 가용성 외 판정 실패는 fail-closed(관리자 아님) */
    @Test
    void orgFailureIsFailClosed() {
        org.grantAdmin(1L);
        org.setFailed(true);
        assertThatThrownBy(() -> guard.requireOwnerOrAdmin(99L, TestAuth.user(1L, "Root")))
                .isInstanceOf(AccessDeniedException.class);
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
