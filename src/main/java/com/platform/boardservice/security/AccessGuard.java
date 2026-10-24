package com.platform.boardservice.security;

import com.platform.boardservice.org.OrgClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 작성자·관리자 판정. 관리자는 <b>org 전역 관리자</b>({@code CheckPermission(GLOBAL, ADMIN)})다 — KC realm 역할
 * {@code ROLE_ADMIN}은 보지 않는다(M1: 위키·ALM·agent와 같은 관리자 하나). org 불능이면 503이 그대로 올라간다.
 */
@Component
public class AccessGuard {

    private final OrgClient orgClient;

    public AccessGuard(OrgClient org) {
        this.orgClient = org;
    }

    /** JWT sub(=userId). 형식이 다른 토큰은 500이 아니라 403으로 거부. */
    public long currentUserId(Authentication auth) {
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("토큰 subject가 사용자 ID 형식이 아닙니다.");
        }
    }

    /** JWT name claim(표시용). */
    public String currentUserName(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String name = jwtAuth.getToken().getClaimAsString("name");
            if (name != null) return name;
        }
        return auth.getName();
    }

    /** org 전역 관리자인가. 판정 실패는 false(fail-closed), org 불능은 503. */
    public boolean isAdmin(Authentication auth) {
        return orgClient.isGlobalAdmin(currentUserId(auth));
    }

    /** 작성자 본인 또는 전역 관리자가 아니면 403. 본인이면 org를 부르지 않는다. */
    public void requireOwnerOrAdmin(long authorId, Authentication auth) {
        if (currentUserId(auth) == authorId) return;
        if (isAdmin(auth)) return;
        throw new AccessDeniedException("작성자 본인 또는 ADMIN만 가능합니다.");
    }
}
