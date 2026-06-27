package com.platform.boardservice.comment;

import com.platform.boardservice.TestAuth;
import com.platform.boardservice.common.NotFoundException;
import com.platform.boardservice.comment.dto.CommentCreateRequest;
import com.platform.boardservice.comment.dto.CommentResponse;
import com.platform.boardservice.post.Post;
import com.platform.boardservice.post.PostRepository;
import com.platform.boardservice.security.AccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest; // Spring Boot 4 패키지
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({CommentService.class, AccessGuard.class})
class CommentServiceTest {

    @Autowired CommentService service;
    @Autowired PostRepository postRepository;

    private Long newPost() {
        return postRepository.save(new Post("t", "c", 42L, "Alice")).getId();
    }

    @Test
    void createsCommentUnderExistingPost() {
        Long postId = newPost();
        CommentResponse res = service.create(postId, new CommentCreateRequest("좋아요"), TestAuth.user(7L, "Bob"));
        assertThat(res.id()).isNotNull();
        assertThat(res.postId()).isEqualTo(postId);
        assertThat(res.authorId()).isEqualTo(7L);
    }

    @Test
    void createOnMissingPostThrows() {
        assertThatThrownBy(() -> service.create(999L, new CommentCreateRequest("x"), TestAuth.user(7L, "Bob")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void otherUserCannotUpdateComment() {
        Long postId = newPost();
        var c = service.create(postId, new CommentCreateRequest("hi"), TestAuth.user(7L, "Bob"));
        assertThatThrownBy(() -> service.update(c.id(),
                new com.platform.boardservice.comment.dto.CommentUpdateRequest("edit"),
                TestAuth.user(8L, "Eve")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listsCommentsByPost() {
        Long postId = newPost();
        service.create(postId, new CommentCreateRequest("a"), TestAuth.user(7L, "Bob"));
        service.create(postId, new CommentCreateRequest("b"), TestAuth.user(7L, "Bob"));
        assertThat(service.list(postId, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }
}
