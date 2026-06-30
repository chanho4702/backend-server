package com.platform.boardservice.post;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class PostControllerTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // sub=userId, ROLE을 명시(jwt() post-processor는 컨버터를 거치지 않으므로 authorities 직접 지정).
    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser(long id, String name) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("name", name).claim("roles", java.util.List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin(long id, String name) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("name", name).claim("roles", java.util.List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Long createPost(long userId) throws Exception {
        String body = mvc.perform(post("/api/board/posts").with(asUser(userId, "Alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
    }

    @Test
    void listIsPublic() throws Exception {
        mvc.perform(get("/api/board/posts")).andExpect(status().isOk());
    }

    @Test
    void createRequiresAuth() throws Exception {
        mvc.perform(post("/api/board/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        mvc.perform(post("/api/board/posts").with(asUser(42L, "Alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    @Test
    void ownerCanUpdate() throws Exception {
        Long id = createPost(42L);
        mvc.perform(put("/api/board/posts/" + id).with(asUser(42L, "Alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t2\",\"content\":\"c2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("t2"));
    }

    @Test
    void otherUserGetsForbidden() throws Exception {
        Long id = createPost(42L);
        mvc.perform(put("/api/board/posts/" + id).with(asUser(7L, "Mallory"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteOthersPost() throws Exception {
        Long id = createPost(42L);
        mvc.perform(delete("/api/board/posts/" + id).with(asAdmin(1L, "Root")))
                .andExpect(status().isNoContent());
    }

    @Test
    void missingPostReturns404() throws Exception {
        mvc.perform(get("/api/board/posts/999999")).andExpect(status().isNotFound());
    }
}
