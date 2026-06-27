package com.platform.boardservice.post.dto;

import com.platform.boardservice.post.Post;

import java.time.Instant;

public record PostResponse(Long id, String title, String content, Long authorId,
                           String authorName, Instant createdAt, Instant updatedAt) {
    public static PostResponse from(Post p) {
        return new PostResponse(p.getId(), p.getTitle(), p.getContent(),
                p.getAuthorId(), p.getAuthorName(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
