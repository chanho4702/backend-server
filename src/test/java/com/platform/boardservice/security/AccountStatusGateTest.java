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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계정 상태 게이트(B1·m2)와 org 전역 관리자 판정(M1) — 게시판 REST 경계에서 본다.
 * 사용자 id는 테스트마다 달리 쓴다(게이트 30초 캐시가 테스트 사이에 새지 않게 evict도 한다).
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountStatusGateTest {

    private static final String LIST = "/api/board/posts";

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

    private static RequestPostProcessor asUser(long id) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("name", "u" + id).claim("roles", List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** KC realm ADMIN 토큰 — org 전역 관리자가 아니면 관리자가 아니다 */
    private static RequestPostProcessor asRealmAdmin(long id) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("name", "u" + id).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private long createPost(long userId) throws Exception {
        String body = mvc.perform(post(LIST).with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
    }

    @Test
    void 활성_계정은_읽고_쓴다() throws Exception {
        orgFake.setStatus(5001, "ACTIVE");
        mvc.perform(get(LIST).with(asUser(5001))).andExpect(status().isOk());
        createPost(5001);
    }

    @Test
    void 승인_대기_계정은_글을_못_쓴다() throws Exception {
        orgFake.setStatus(5002, "PENDING");
        mvc.perform(post(LIST).with(asUser(5002))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("승인 대기 중인 계정입니다"));
    }

    @Test
    void 승인_대기_계정은_댓글도_읽기도_막힌다() throws Exception {
        long postId = createPost(5003);
        orgFake.setStatus(5004, "PENDING");
        mvc.perform(post("/api/board/posts/" + postId + "/comments").with(asUser(5004))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(LIST).with(asUser(5004)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("승인 대기 중인 계정입니다"));
    }

    @Test
    void 정지_비활성_계정은_막힌다() throws Exception {
        orgFake.setStatus(5005, "SUSPENDED");
        orgFake.setStatus(5006, "DEACTIVATED");
        mvc.perform(get(LIST).with(asUser(5005)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("정지된 계정입니다"));
        mvc.perform(get(LIST).with(asUser(5006)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("비활성된 계정입니다"));
    }

    /** org에 행이 없는 사람은 승인 대기로 본다 — "알 수 없음 = 통과"가 없다(fail-closed) */
    @Test
    void org에_없는_사용자는_승인_대기로_막힌다() throws Exception {
        orgFake.markAbsent(5007);
        mvc.perform(post(LIST).with(asUser(5007))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("승인 대기 중인 계정입니다"));
    }

    @Test
    void 모르는_상태_값도_막힌다() throws Exception {
        orgFake.setStatus(5008, "LOCKED");
        mvc.perform(get(LIST).with(asUser(5008))).andExpect(status().isForbidden());
    }

    /** org 불능은 403도 빈 목록도 아닌 503 — 그리고 캐시되지 않는다 */
    @Test
    void org_불능이면_503이고_캐시하지_않는다() throws Exception {
        orgFake.setUnavailable(true);
        mvc.perform(get(LIST).with(asUser(5009)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"))
                .andExpect(jsonPath("$.message").value("권한 서비스에 연결할 수 없습니다"));

        orgFake.setUnavailable(false);
        mvc.perform(get(LIST).with(asUser(5009))).andExpect(status().isOk());
        // 불능 시 한 번 재시도(콜드 스타트 흡수) → 실패 2회 + 복구 후 1회
        assertThat(orgFake.statusCalls()).hasSize(3);
    }

    /** 가용성 외 조회 실패는 통과가 아니라 거부(fail-closed) */
    @Test
    void 조회_실패는_거부한다() throws Exception {
        orgFake.setFailed(true);
        mvc.perform(get(LIST).with(asUser(5010)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void 상태는_캐시되어_요청마다_묻지_않는다() throws Exception {
        mvc.perform(get(LIST).with(asUser(5011))).andExpect(status().isOk());
        mvc.perform(get(LIST).with(asUser(5011))).andExpect(status().isOk());
        mvc.perform(get(LIST).with(asUser(5011))).andExpect(status().isOk());
        assertThat(orgFake.statusCalls()).hasSize(1);
    }

    @Test
    void 헬스체크는_게이트를_타지_않는다() throws Exception {
        orgFake.setUnavailable(true);
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        assertThat(orgFake.statusCalls()).isEmpty();
    }

    @Test
    void 익명_읽기는_기본_차단() throws Exception {
        mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        assertThat(orgFake.statusCalls()).isEmpty();
    }

    // ── 관리자 = org 전역 관리자(M1) ───────────────────────────────

    @Test
    void org_전역_관리자는_남의_글을_지운다() throws Exception {
        long postId = createPost(5012);
        orgFake.grantAdmin(5013);
        mvc.perform(delete("/api/board/posts/" + postId).with(asUser(5013))).andExpect(status().isNoContent());
    }

    @Test
    void realm_ADMIN만으로는_남의_글을_못_지운다() throws Exception {
        long postId = createPost(5014);
        mvc.perform(delete("/api/board/posts/" + postId).with(asRealmAdmin(5015)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void 관리자_판정_중_org_불능이면_503() throws Exception {
        long postId = createPost(5016);
        orgFake.grantAdmin(5017);
        mvc.perform(get(LIST).with(asUser(5017))).andExpect(status().isOk()); // 게이트 판정을 캐시에 올려 둔다
        orgFake.setUnavailable(true);
        mvc.perform(delete("/api/board/posts/" + postId).with(asUser(5017)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
    }
}
