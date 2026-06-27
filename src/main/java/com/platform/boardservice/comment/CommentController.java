package com.platform.boardservice.comment;

import com.platform.boardservice.comment.dto.CommentCreateRequest;
import com.platform.boardservice.comment.dto.CommentResponse;
import com.platform.boardservice.comment.dto.CommentUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping("/posts/{postId}/comments")
    public Page<CommentResponse> list(@PathVariable Long postId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(postId, pageable);
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest req, Authentication auth) {
        return service.create(postId, req, auth);
    }

    @PutMapping("/comments/{id}")
    public CommentResponse update(@PathVariable Long id,
            @Valid @RequestBody CommentUpdateRequest req, Authentication auth) {
        return service.update(id, req, auth);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth);
    }
}
