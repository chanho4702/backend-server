package com.platform.boardservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT 디코더(JWKS + issuer/audience 검증)·roles→ROLE_ 변환기는 common-starter가 준다(S-02). 여기는 경로 정책만.
 *
 * <p>읽기도 로그인이 기본이다(B1) — 익명 GET이 작성자 이름까지 흘리고 있었다. 익명 열람은 설치 옵션
 * {@code BOARD_ANONYMOUS_READ=true}일 때만 연다(기본 false). 로그인한 요청은 어느 쪽이든 계정 상태 게이트
 * ({@code AccountStatusInterceptor}, ACTIVE만)를 탄다.
 */
@Configuration
@EnableMethodSecurity // 수정/삭제는 AccessGuard(서비스 계층)로 판정.
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter,
                                    @Value("${platform.board.anonymous-read:false}") boolean anonymousRead) throws Exception {
        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> {
                    // 내부망 전용 헬스/빌드정보 — 게이트웨이 대시보드가 인증 없이 프로브한다.
                    // 노출은 이 둘뿐이다(management.endpoints.web.exposure.include).
                    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                    if (anonymousRead) {
                        auth.requestMatchers(HttpMethod.GET, "/api/board/posts/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
