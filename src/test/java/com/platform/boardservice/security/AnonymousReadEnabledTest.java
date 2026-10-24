package com.platform.boardservice.security;

import com.platform.boardservice.org.FakeOrgClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 설치 옵션 BOARD_ANONYMOUS_READ=true — 익명 GET만 열리고, 쓰기·토큰 단 요청의 계정 상태 게이트는 그대로다. */
@SpringBootTest(properties = "platform.board.anonymous-read=true")
@ActiveProfiles("test")
class AnonymousReadEnabledTest {

    @Autowired WebApplicationContext context;
    @Autowired FakeOrgClient orgFake;
    @Autowired AccountStatusInterceptor gate;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        orgFake.reset();
        gate.evictAll();
    }

    @AfterEach
    void cleanup() {
        orgFake.reset();
        gate.evictAll();
    }

    @Test
    void 익명_목록_조회가_열린다() throws Exception {
        mvc.perform(get("/api/board/posts")).andExpect(status().isOk());
    }

    @Test
    void 익명_쓰기는_여전히_401() throws Exception {
        mvc.perform(post("/api/board/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰을_단_승인_대기_계정은_여전히_막힌다() throws Exception {
        orgFake.setStatus(6001, "PENDING");
        mvc.perform(get("/api/board/posts").with(jwt()
                        .jwt(j -> j.subject("6001").claim("roles", List.of("USER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}
