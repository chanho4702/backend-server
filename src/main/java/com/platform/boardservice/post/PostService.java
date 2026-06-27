package com.platform.boardservice.post;

import com.platform.boardservice.common.NotFoundException;
import com.platform.boardservice.post.dto.PostCreateRequest;
import com.platform.boardservice.post.dto.PostResponse;
import com.platform.boardservice.post.dto.PostSummaryResponse;
import com.platform.boardservice.security.AccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository repository;
    private final AccessGuard accessGuard;

    public PostService(PostRepository repository, AccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public PostResponse create(PostCreateRequest req, Authentication auth) {
        Post post = new Post(req.title(), req.content(),
                accessGuard.currentUserId(auth), accessGuard.currentUserName(auth));
        return PostResponse.from(repository.save(post));
    }

    @Transactional(readOnly = true)
    public PostResponse get(Long id) {
        return PostResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(PostSummaryResponse::from);
    }

    Post find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다: " + id));
    }
}
