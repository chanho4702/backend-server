package com.platform.boardservice.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * board의 오류 모양 {@code {error: 코드, message}}(myFront 계약)이 common-starter 0.17.0의 공용 핸들러
 * (AccessDenied·검증 등 새로 늘어난 것 포함)보다 먼저 잡히는지 — 스타터 버전이 올라도 board 응답이 바뀌지 않아야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ErrorContractPrecedenceTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor asUser(long id) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("name", "u" + id).claim("roles", List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void 없는_글은_board_모양_404() throws Exception {
        mvc.perform(get("/api/board/posts/987654321"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void 남의_글_수정은_board_모양_403_스타터_AccessDenied보다_먼저() throws Exception {
        String body = mvc.perform(post("/api/board/posts").with(asUser(1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);

        mvc.perform(put("/api/board/posts/" + id).with(asUser(2))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("작성자 본인 또는 ADMIN만 가능합니다."));
    }

    @Test
    void 검증_실패는_board_모양_400_스타터_검증_핸들러보다_먼저() throws Exception {
        mvc.perform(post("/api/board/posts").with(asUser(1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"content\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /**
     * board 핸들러가 다루지 않는 것은 스타터 계약({@code {error: 한국어}})으로 떨어진다. myFront boardStore는
     * 상태 코드만 보고 자기 문구를 띄우므로 본문 모양은 화면에 영향이 없다 — 여기서는 원문(클래스명) 비노출만 본다.
     */
    @Test
    void 경로_형식_오류는_스타터_문구_400() throws Exception {
        mvc.perform(get("/api/board/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("요청 값의 형식이 올바르지 않습니다"));
    }
}
