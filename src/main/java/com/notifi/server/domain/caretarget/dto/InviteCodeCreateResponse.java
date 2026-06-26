package com.notifi.server.domain.caretarget.dto;

import java.time.Instant;

public record InviteCodeCreateResponse(
        String code,
        String inviteUrl,
        Instant expiresAt
) {}
