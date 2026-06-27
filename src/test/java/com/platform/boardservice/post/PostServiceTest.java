package com.platform.boardservice.post;

import com.platform.boardservice.TestAuth;
import com.platform.boardservice.common.NotFoundException;
import com.platform.boardservice.post.dto.PostCreateRequest;
import com.platform.boardservice.post.dto.PostResponse;
import com.platform.boardservice.post.dto.PostUpdateRequest;
import com.platform.boardservice.security.AccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest; // Spring Boot 4 패키지
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({PostService.class, AccessGuard.class})
class PostServiceTest {

    @Autowired PostService service;

    @Test
    void createsPostWithAuthorFromToken() {
        PostResponse res = service.create(new PostCreateRequest("제목", "내용"), TestAuth.user(42L, "Alice"));
        assertThat(res.id()).isNotNull();
        assertThat(res.authorId()).isEqualTo(42L);
        assertThat(res.authorName()).isEqualTo("Alice");
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void ownerCanUpdate() {
        var created = service.create(new com.platform.boardservice.post.dto.PostCreateRequest("t", "c"),
                TestAuth.user(42L, "Alice"));
        var res = service.update(created.id(),
                new com.platform.boardservice.post.dto.PostUpdateRequest("t2", "c2"),
                TestAuth.user(42L, "Alice"));
        assertThat(res.title()).isEqualTo("t2");
    }

    @Test
    void otherUserCannotUpdate() {
        var created = service.create(new com.platform.boardservice.post.dto.PostCreateRequest("t", "c"),
                TestAuth.user(42L, "Alice"));
        assertThatThrownBy(() -> service.update(created.id(),
                new com.platform.boardservice.post.dto.PostUpdateRequest("x", "y"),
                TestAuth.user(7L, "Mallory")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void adminCanDeleteOthersPost() {
        var created = service.create(new com.platform.boardservice.post.dto.PostCreateRequest("t", "c"),
                TestAuth.user(42L, "Alice"));
        service.delete(created.id(), TestAuth.admin(1L, "Root"));
        assertThatThrownBy(() -> service.get(created.id())).isInstanceOf(NotFoundException.class);
    }
}
