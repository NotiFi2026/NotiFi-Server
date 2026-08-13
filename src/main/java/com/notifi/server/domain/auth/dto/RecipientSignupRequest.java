package com.notifi.server.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 노인 연결코드 가입·재연결 요청. role은 서버가 CARE_RECIPIENT로 고정하므로 받지 않는다.
 *
 * <p>{@code email}·{@code password}·{@code name}이 전부 선택인 이유: <b>노인은 자격증명을
 * 소유하지 않는다.</b> 보호자가 대신 만들어 주는데, 그렇게 만든 값은 아무도 기억하지 않아
 * 로그아웃되면 복구가 안 된다. 생략하면 서버가 생성하고, 로그인 경로는 연결코드 하나로 모인다.
 *
 * <p>이미 연결된 노인이면 재연결로 처리되며 이때 이 필드들은 전부 무시된다 —
 * 기존 계정을 그대로 쓴다.
 */
public record RecipientSignupRequest(
        @NotBlank String code,
        @Email @Size(max = 255) String email,
        @Size(min = 8, max = 100) String password,
        @Size(max = 100) String name
) {}
