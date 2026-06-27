package com.platform.boardservice.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {

    /** JWT sub(=userId). */
    public long currentUserId(Authentication auth) {
        return Long.parseLong(auth.getName());
    }

    /** JWT name claim(표시용). */
    public String currentUserName(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String name = jwtAuth.getToken().getClaimAsString("name");
            if (name != null) return name;
        }
        return auth.getName();
    }

    public boolean isAdmin(Authentication auth) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(a.getAuthority())) return true;
        }
        return false;
    }

    /** 작성자 본인 또는 ADMIN이 아니면 403. */
    public void requireOwnerOrAdmin(long authorId, Authentication auth) {
        if (isAdmin(auth)) return;
        if (currentUserId(auth) == authorId) return;
        throw new AccessDeniedException("작성자 본인 또는 ADMIN만 가능합니다.");
    }
}
