package com.platform.boardservice.org;

/**
 * org-service 조회 경계 — 사람의 상태(구성원 원장)와 전역 관리자 판정의 정본은 org다(루트 CLAUDE.md 확정 결정).
 * board는 JWT의 {@code sub}만 믿고, 계정 상태·관리자 여부는 여기서 묻는다. KC realm 역할(ROLE_ADMIN)로 관리자를
 * 판정하지 않는다 — 위키·ALM·agent와 관리자가 둘로 갈라진다(M1).
 *
 * <p>테스트는 이 인터페이스를 페이크로 바꾼다. gRPC 구현의 코드별 분기는 {@code GrpcOrgClientTest}가 따로 본다.
 */
public interface OrgClient {

    /** 조회가 어떻게 끝났는가 — {@code UNAVAILABLE}만 가용성 장애(503)이고, {@code FAILED}는 그 밖의 오류다. */
    enum Outcome { OK, UNAVAILABLE, FAILED }

    /**
     * 구성원 상태 조회 결과. {@code status}는 {@code PENDING|ACTIVE|SUSPENDED|DEACTIVATED}이고, org에 그 사람의 행이
     * 없으면 null이다(OK인데 null). 조회가 실패했으면 status는 의미가 없다.
     */
    record StatusLookup(Outcome outcome, String status) {

        public static StatusLookup of(String status) {
            return new StatusLookup(Outcome.OK, status);
        }

        public static StatusLookup absent() {
            return new StatusLookup(Outcome.OK, null);
        }
    }

    /** 던지지 않는다 — 왜 비었는지를 {@link Outcome}으로 알리고, 그걸 403으로 볼지 503으로 볼지는 호출측이 정한다. */
    StatusLookup memberStatus(long userId);

    /**
     * org {@code CheckPermission(GLOBAL, "", ADMIN)}. 허용이면 true, 거부·가용성 외 오류면 false(fail-closed).
     * org 불능(UNAVAILABLE·DEADLINE_EXCEEDED)이면 {@link com.platform.common.error.ServiceUnavailableException}을 던진다 —
     * 장애를 "관리자가 아니다"로 말하지 않는다.
     */
    boolean isGlobalAdmin(long userId);
}
