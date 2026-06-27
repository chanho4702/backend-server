package com.platform.boardservice.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(@NotBlank(message = "content는 필수입니다.") String content) {
}
