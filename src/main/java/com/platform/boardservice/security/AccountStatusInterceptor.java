package com.platform.boardservice.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.platform.boardservice.org.OrgClient;
import com.platform.common.error.ServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 계정 상태 게이트 — 게시판 REST 전체에서 org 구성원 상태가 {@code ACTIVE}인 사람만 통과한다(B1·m2, 위키·ALM 게이트와 같은 문구).
 *
 * <p>왜 필요한가: 그전 board는 로그인만 봤다. 승인 대기(PENDING) 계정이 글·댓글을 쓰고, 정지된 계정도 AT가 살아 있는 동안
 * (최대 15분) 계속 썼다. 구성원 상태의 정본은 org다.
 *
 * <p>규칙은 fail-closed 하나다 — <b>ACTIVE가 아니면 막는다.</b>
 * <ul>
 *   <li>{@code PENDING}·{@code SUSPENDED}·{@code DEACTIVATED} → 403, 문구는 위키·ALM과 한 글자도 다르지 않다.</li>
 *   <li><b>org에 행이 없으면 승인 대기로 본다</b>(403 "승인 대기 중인 계정입니다"). 알 수 없는 사람을 통과시키지 않는다.</li>
 *   <li>org 불능(UNAVAILABLE·DEADLINE) → 한 번 재시도 후 503. 장애를 "정지된 계정"으로 말하지도, 통과시키지도 않는다.</li>
 *   <li>그 밖의 조회 실패 → 403(판정 실패를 알리는 문구). 모르는 상태 값도 403.</li>
 * </ul>
 *
 * <p>익명 요청(토큰 없음)은 여기서 판단하지 않는다 — 익명 열람을 열지는 {@code SecurityConfig}의
 * {@code BOARD_ANONYMOUS_READ}가 정한다. 토큰을 단 요청은 읽기든 쓰기든 이 게이트를 탄다.
 *
 * <p>정상 판정만 30초 캐시한다(위키·ALM 게이트와 같은 값). 대가: 방금 정지된 사람이 최대 30초 더 쓸 수 있다
 * (그전에는 AT 수명 15분이었다). 실패·장애는 캐시하지 않는다.
 */
@Component
@Slf4j
public class AccountStatusInterceptor implements HandlerInterceptor {

    static final String ACTIVE = "ACTIVE";
    static final String PENDING_MESSAGE = "승인 대기 중인 계정입니다";
    static final String SUSPENDED_MESSAGE = "정지된 계정입니다";
    static final String DEACTIVATED_MESSAGE = "비활성된 계정입니다";
    static final String UNKNOWN_MESSAGE = "계정 상태를 확인하지 못해 거부했습니다 — 잠시 후 다시 시도하세요";
    static final String UNAVAILABLE_MESSAGE = "권한 서비스에 연결할 수 없습니다";

    /** 캐시 값 — org에 행이 없으면 빈 문자열(= 승인 대기 취급) */
    private final Cache<Long, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(10_000)
            .build();

    private final OrgClient orgClient;

    public AccountStatusInterceptor(OrgClient org) {
        this.orgClient = org;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) return true; // 익명 — 체인이 정한다
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("토큰 subject가 사용자 ID 형식이 아닙니다.");
        }
        String status = cache.get(userId, this::lookup);
        if (ACTIVE.equals(status)) return true;
        log.warn("계정 상태로 차단: user={} status={} {} {}",
                userId, status.isEmpty() ? "(org에 없음)" : status, request.getMethod(), request.getRequestURI());
        throw new AccessDeniedException(message(status));
    }

    /** 캐시를 비운다 — 테스트 격리용. org가 상태 변경을 알리는 경로가 생기면 그 훅이 부른다. */
    public void evictAll() {
        cache.invalidateAll();
    }

    /** 정상 판정만 값으로 돌려준다(캐시된다). 장애·실패는 던진다(캐시되지 않는다). */
    private String lookup(long userId) {
        OrgClient.StatusLookup result = orgClient.memberStatus(userId);
        if (result.outcome() == OrgClient.Outcome.UNAVAILABLE) {
            // 콜드 스타트·순간 끊김 한 번은 흡수한다 — 모든 게시판 요청 앞에 서는 게이트다(위키·ALM과 같다)
            result = orgClient.memberStatus(userId);
        }
        return switch (result.outcome()) {
            case UNAVAILABLE -> throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
            case FAILED -> throw new AccessDeniedException(UNKNOWN_MESSAGE);
            case OK -> result.status() == null ? "" : result.status();
        };
    }

    static String message(String status) {
        return switch (status) {
            case "", "PENDING" -> PENDING_MESSAGE;
            case "SUSPENDED" -> SUSPENDED_MESSAGE;
            case "DEACTIVATED" -> DEACTIVATED_MESSAGE;
            default -> UNKNOWN_MESSAGE;
        };
    }
}
