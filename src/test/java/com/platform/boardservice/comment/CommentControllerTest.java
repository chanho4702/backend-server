package com.platform.boardservice.comment;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CommentControllerTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor asUser(long id, String name) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("name", name).claim("roles", List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Long createPost(long userId) throws Exception {
        String body = mvc.perform(post("/api/board/posts").with(asUser(userId, "Alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
    }

    private Long createComment(long postId, long userId) throws Exception {
        String body = mvc.perform(post("/api/board/posts/" + postId + "/comments").with(asUser(userId, "Bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
    }

    @Test
    void createRequiresAuth() throws Exception {
        Long postId = createPost(42L);
        mvc.perform(post("/api/board/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listIsPublic() throws Exception {
        Long postId = createPost(42L);
        createComment(postId, 7L);
        mvc.perform(get("/api/board/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void ownerCanUpdateComment() throws Exception {
        Long postId = createPost(42L);
        Long cid = createComment(postId, 7L);
        mvc.perform(put("/api/board/comments/" + cid).with(asUser(7L, "Bob"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("edited"));
    }

    @Test
    void otherUserGetsForbidden() throws Exception {
        Long postId = createPost(42L);
        Long cid = createComment(postId, 7L);
        mvc.perform(delete("/api/board/comments/" + cid).with(asUser(8L, "Eve")))
                .andExpect(status().isForbidden());
    }

    @Test
    void commentOnMissingPostReturns404() throws Exception {
        mvc.perform(post("/api/board/posts/999999/comments").with(asUser(7L, "Bob"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }
}
