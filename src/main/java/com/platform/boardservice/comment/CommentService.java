package com.platform.boardservice.comment;

import com.platform.boardservice.comment.dto.CommentCreateRequest;
import com.platform.boardservice.comment.dto.CommentResponse;
import com.platform.boardservice.comment.dto.CommentUpdateRequest;
import com.platform.boardservice.common.NotFoundException;
import com.platform.boardservice.post.PostRepository;
import com.platform.boardservice.security.AccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository repository;
    private final PostRepository postRepository;
    private final AccessGuard accessGuard;

    public CommentService(CommentRepository repository, PostRepository postRepository, AccessGuard accessGuard) {
        this.repository = repository;
        this.postRepository = postRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public CommentResponse create(Long postId, CommentCreateRequest req, Authentication auth) {
        if (!postRepository.existsById(postId)) {
            throw new NotFoundException("게시글을 찾을 수 없습니다: " + postId);
        }
        Comment comment = new Comment(postId, req.content(),
                accessGuard.currentUserId(auth), accessGuard.currentUserName(auth));
        return CommentResponse.from(repository.save(comment));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> list(Long postId, Pageable pageable) {
        return repository.findByPostId(postId, pageable).map(CommentResponse::from);
    }

    @Transactional
    public CommentResponse update(Long id, CommentUpdateRequest req, Authentication auth) {
        Comment comment = find(id);
        accessGuard.requireOwnerOrAdmin(comment.getAuthorId(), auth);
        comment.edit(req.content());
        return CommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long id, Authentication auth) {
        Comment comment = find(id);
        accessGuard.requireOwnerOrAdmin(comment.getAuthorId(), auth);
        repository.delete(comment);
    }

    private Comment find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다: " + id));
    }
}
