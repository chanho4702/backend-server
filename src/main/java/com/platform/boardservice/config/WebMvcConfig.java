package com.platform.boardservice.config;

import com.platform.boardservice.security.AccountStatusInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 계정 상태 게이트를 게시판 REST({@code /api/board/**}) 전체에 건다. {@code /actuator/**}는 뺀다 — 헬스체크까지 org에
 * 묶으면 org가 죽을 때 board도 죽은 것으로 보고된다(게이트웨이 상태판 오진).
 *
 * <p>인터셉터를 고른 이유(위키·ALM과 같다): 필터에서 던진 예외는 {@code @RestControllerAdvice}가 잡지 못해 board 오류
 * 모양({@code {error, message}})을 손으로 다시 써야 한다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AccountStatusInterceptor accountStatus;

    public WebMvcConfig(AccountStatusInterceptor accountStatus) {
        this.accountStatus = accountStatus;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accountStatus).addPathPatterns("/api/board/**");
    }
}
