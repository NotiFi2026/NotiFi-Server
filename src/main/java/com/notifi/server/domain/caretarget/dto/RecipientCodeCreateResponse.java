package com.notifi.server.domain.caretarget.dto;

import java.time.Instant;

/** 노인 계정 연결코드 발급 응답 — 노인 앱에서 코드를 직접 입력하므로 공유 링크는 없다. */
public record RecipientCodeCreateResponse(
        String code,
        Instant expiresAt
) {}
