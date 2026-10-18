package com.platform.boardservice.post;

import com.platform.boardservice.comment.CommentRepository;
import com.platform.boardservice.common.InvalidQueryException;
import com.platform.boardservice.common.NotFoundException;
import com.platform.boardservice.post.dto.PostCreateRequest;
import com.platform.boardservice.post.dto.PostResponse;
import com.platform.boardservice.post.dto.PostSummaryResponse;
import com.platform.boardservice.post.dto.PostUpdateRequest;
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
    private final CommentRepository commentRepository;

    public PostService(PostRepository repository, AccessGuard accessGuard, CommentRepository commentRepository) {
        this.repository = repository;
        this.accessGuard = accessGuard;
        this.commentRepository = commentRepository;
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

    /** 검색어 최대 길이 — 넘으면 400(validation_failed). */
    public static final int MAX_QUERY_LENGTH = 100;

    @Transactional(readOnly = true)
    public Page<PostSummaryResponse> list(Pageable pageable) {
        return list(null, pageable);
    }

    /** q 가 비었으면 전체, 아니면 제목·본문 부분 일치(와일드카드는 글자 그대로). 페이징·정렬은 그대로. */
    @Transactional(readOnly = true)
    public Page<PostSummaryResponse> list(String q, Pageable pageable) {
        String query = q == null ? "" : q.strip();
        if (query.isEmpty()) {
            return repository.findAll(pageable).map(PostSummaryResponse::from);
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidQueryException("검색어는 " + MAX_QUERY_LENGTH + "자 이하여야 합니다.");
        }
        return repository.search(likePattern(query), pageable).map(PostSummaryResponse::from);
    }

    /** LIKE 패턴: 소문자화 + 백슬래시·%·_ 이스케이프(이스케이프 문자는 백슬래시) + 앞뒤 %. */
    static String likePattern(String query) {
        String escaped = query.toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    @Transactional
    public PostResponse update(Long id, PostUpdateRequest req, Authentication auth) {
        Post post = find(id);
        accessGuard.requireOwnerOrAdmin(post.getAuthorId(), auth);
        post.edit(req.title(), req.content());
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(Long id, Authentication auth) {
        Post post = find(id);
        accessGuard.requireOwnerOrAdmin(post.getAuthorId(), auth);
        commentRepository.deleteByPostId(id); // 글 삭제 시 댓글 cascade 정리(H2/PG 모두 보장)
        repository.delete(post);
    }

    Post find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다: " + id));
    }
}
