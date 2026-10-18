package com.platform.boardservice.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 제목·본문 부분 일치(대소문자 무시). pattern 은 호출자가 LIKE 와일드카드(백슬래시·%·_)를
     * 이스케이프하고 앞뒤 %를 붙여 넘긴다({@link PostService#likePattern}). 이스케이프 문자는 백슬래시.
     */
    @Query("select p from Post p where lower(p.title) like :pattern escape '\\' "
            + "or lower(p.content) like :pattern escape '\\'")
    Page<Post> search(@Param("pattern") String pattern, Pageable pageable);
}
