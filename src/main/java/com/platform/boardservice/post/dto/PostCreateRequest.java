package com.platform.boardservice.post.dto;

import jakarta.validation.constraints.NotBlank;

public record PostCreateRequest(
        @NotBlank(message = "title은 필수입니다.") String title,
        @NotBlank(message = "content는 필수입니다.") String content) {
}
