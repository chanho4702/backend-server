package com.platform.boardservice.post.dto;

import com.platform.boardservice.post.Post;

import java.time.Instant;

public record PostSummaryResponse(Long id, String title, String authorName, Instant createdAt) {
    public static PostSummaryResponse from(Post p) {
        return new PostSummaryResponse(p.getId(), p.getTitle(), p.getAuthorName(), p.getCreatedAt());
    }
}
