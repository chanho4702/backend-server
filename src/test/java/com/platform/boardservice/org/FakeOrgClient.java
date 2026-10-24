package com.platform.boardservice.org;

import com.platform.common.error.ServiceUnavailableException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트 전용 org 페이크 — test 소스셋 컴포넌트 스캔으로 등록되고 gRPC 구현을 대신한다({@code @Primary}).
 *
 * <p><b>기본값은 "모르는 사람은 ACTIVE"다.</b> 상태를 다루지 않는 기존 테스트(글·댓글 CRUD)가 게이트 때문에 깨지지 않게
 * 하려는 페이크 쪽 편의다 — 실제 게이트는 org에 없는 사람을 막는다. 그 경로는 {@link #markAbsent}로 검증한다.
 * 관리자는 기본으로 아무도 아니다({@link #grantAdmin}).
 */
@Component
@Primary
public class FakeOrgClient implements OrgClient {

    private final Map<Long, String> statuses = new ConcurrentHashMap<>();
    private final Set<Long> absent = ConcurrentHashMap.newKeySet();
    private final Set<Long> admins = ConcurrentHashMap.newKeySet();
    private final List<Long> statusCalls = new CopyOnWriteArrayList<>();
    private final List<Long> adminCalls = new CopyOnWriteArrayList<>();
    private volatile boolean unavailable;
    private volatile boolean failed;

    public void setStatus(long userId, String status) { statuses.put(userId, status); }
    public void markAbsent(long userId) { absent.add(userId); }
    public void grantAdmin(long userId) { admins.add(userId); }
    public void setUnavailable(boolean value) { unavailable = value; }
    public void setFailed(boolean value) { failed = value; }
    public List<Long> statusCalls() { return statusCalls; }
    public List<Long> adminCalls() { return adminCalls; }

    public void reset() {
        statuses.clear();
        absent.clear();
        admins.clear();
        statusCalls.clear();
        adminCalls.clear();
        unavailable = false;
        failed = false;
    }

    @Override
    public StatusLookup memberStatus(long userId) {
        statusCalls.add(userId);
        if (unavailable) return new StatusLookup(Outcome.UNAVAILABLE, null);
        if (failed) return new StatusLookup(Outcome.FAILED, null);
        if (absent.contains(userId)) return StatusLookup.absent();
        return StatusLookup.of(statuses.getOrDefault(userId, "ACTIVE"));
    }

    @Override
    public boolean isGlobalAdmin(long userId) {
        adminCalls.add(userId);
        if (unavailable) throw new ServiceUnavailableException("권한 서비스에 연결할 수 없습니다");
        if (failed) return false;
        return admins.contains(userId);
    }
}
