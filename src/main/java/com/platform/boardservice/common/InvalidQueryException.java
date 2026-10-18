package com.platform.boardservice.common;

/** 요청 파라미터(검색어 등)가 규칙을 어겼다 — 400 validation_failed 로 매핑된다. */
public class InvalidQueryException extends RuntimeException {
    public InvalidQueryException(String message) {
        super(message);
    }
}
