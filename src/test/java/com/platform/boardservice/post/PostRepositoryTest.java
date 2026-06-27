package com.platform.boardservice.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PostRepositoryTest {

    @Autowired PostRepository repository;

    @Test
    void savesAndReadsPost() {
        Post saved = repository.save(new Post("제목", "내용", 42L, "Alice"));
        assertThat(saved.getId()).isNotNull();
        Post found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("제목");
        assertThat(found.getAuthorId()).isEqualTo(42L);
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
