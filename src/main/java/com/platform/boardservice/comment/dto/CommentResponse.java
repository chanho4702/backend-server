package com.platform.boardservice.comment.dto;

import com.platform.boardservice.comment.Comment;

import java.time.Instant;

public record CommentResponse(Long id, Long postId, String content, Long authorId,
                              String authorName, Instant createdAt, Instant updatedAt) {
    public static CommentResponse from(Comment c) {
        return new CommentResponse(c.getId(), c.getPostId(), c.getContent(),
                c.getAuthorId(), c.getAuthorName(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
