package com.platform.boardservice.security;

import com.platform.boardservice.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

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
}
