package com.platform.boardservice.comment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest; // Spring Boot 4 패키지
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CommentRepositoryTest {

    @Autowired CommentRepository repository;

    @Test
    void savesAndFindsByPost() {
        repository.save(new Comment(1L, "댓글", 42L, "Alice"));
        repository.save(new Comment(1L, "댓글2", 43L, "Bob"));
        repository.save(new Comment(2L, "다른글댓글", 42L, "Alice"));
        assertThat(repository.findByPostId(1L, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void deletesByPost() {
        repository.save(new Comment(1L, "댓글", 42L, "Alice"));
        repository.deleteByPostId(1L);
        assertThat(repository.findByPostId(1L, PageRequest.of(0, 10)).getTotalElements()).isZero();
    }
}
