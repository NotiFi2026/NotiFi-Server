package com.notifi.server.domain.caretarget.token;

/** 노인 본인 계정 연결코드 페이로드 — 보호자 초대코드(InviteCodePayload)와 Redis 네임스페이스 분리. */
public record RecipientCodePayload(
        Long careTargetId,
        Long issuedBy
) {}
