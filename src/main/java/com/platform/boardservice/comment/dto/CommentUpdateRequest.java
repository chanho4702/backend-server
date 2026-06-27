package com.platform.boardservice.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentUpdateRequest(@NotBlank(message = "content는 필수입니다.") String content) {
}
