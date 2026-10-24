package com.platform.boardservice.common;

import com.platform.common.error.ServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** board는 {error: 코드, message} 모양을 쓴다(myFront 계약) — 스타터의 {error} 핸들러보다 먼저 잡는다. */
@org.springframework.core.annotation.Order(0)
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "forbidden", "message", e.getMessage()));
    }

    /** org 불능(계정 상태 게이트·관리자 판정) — 권한 없음(403)이나 빈 목록으로 위장하지 않는다. */
    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<Map<String, Object>> unavailable(ServiceUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "service_unavailable", "message", e.getMessage()));
    }

    @ExceptionHandler(InvalidQueryException.class)
    ResponseEntity<Map<String, Object>> invalidQuery(InvalidQueryException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "validation_failed", "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "validation_failed", "message", msg));
    }
}
