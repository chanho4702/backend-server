package com.platform.boardservice.post;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Post(String title, String content, Long authorId, String authorName) {
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(String title, String content) {
        this.title = title;
        this.content = content;
        this.updatedAt = Instant.now();
    }
}
