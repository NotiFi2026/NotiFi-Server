package com.notifi.server.global.security.jwt;

import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * JWT 생성·검증·파싱.
 * 토큰 발급(createAccessToken/createRefreshToken)은 #2 Auth 서비스에서 호출.
 * 이 클래스는 stateless — 토큰 저장/블랙리스트 없음 (리프레시 관리는 #2에서 Redis 활용).
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 토큰 용도 클레임 — 리프레시 토큰이 액세스 토큰으로 통용되는 것을 차단 */
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final long accessTtl;             // seconds
    private final long refreshTtl;            // seconds
    private final long recipientRefreshTtl;   // seconds

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-ttl}") long accessTtl,
            @Value("${jwt.refresh-token-ttl}") long refreshTtl,
            @Value("${jwt.recipient-refresh-token-ttl}") long recipientRefreshTtl
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.recipientRefreshTtl = recipientRefreshTtl;
    }

    public String createAccessToken(Long userId, String role) {
        return buildToken(userId, role, accessTtl, TYPE_ACCESS);
    }

    public String createRefreshToken(Long userId, String role) {
        return buildToken(userId, role, refreshTokenTtlFor(role), TYPE_REFRESH);
    }

    /**
     * 역할별 리프레시 수명. 노인은 훨씬 길게 잡는다.
     *
     * <p>보호자는 로그아웃돼도 이메일·비밀번호로 다시 들어오면 된다. 노인은 그 자격증명을
     * 모르는 것이 정상이라(보호자가 만들어 준다) 세션이 끊기면 <b>보호자가 연결코드를 새로
     * 발급해 줄 때까지 아무것도 못 한다.</b> 그동안 음성 확인 푸시를 받아도 응답할 수 없어
     * 낙상 오탐이 그대로 119까지 올라간다. 애초에 끊기지 않게 하는 것이 첫 방어선이다.
     */
    public long refreshTokenTtlFor(String role) {
        return Role.CARE_RECIPIENT.name().equals(role) ? recipientRefreshTtl : refreshTtl;
    }

    /**
     * 액세스 토큰에서 Authentication 추출. 리프레시 토큰은 거부한다.
     * 만료 → TOKEN_EXPIRED, 위조/형식 오류/용도 불일치 → INVALID_CREDENTIALS.
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        requireType(claims, TYPE_ACCESS);
        Long userId = Long.parseLong(claims.getSubject());
        String role  = claims.get("role", String.class);
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    /**
     * 리프레시 토큰 검증 후 userId 반환. 액세스 토큰은 거부한다.
     */
    public Long getRefreshTokenUserId(String token) {
        Claims claims = parseClaims(token);
        requireType(claims, TYPE_REFRESH);
        return Long.parseLong(claims.getSubject());
    }

    // ── private ───────────────────────────────────────────────────────────

    private String buildToken(Long userId, String role, long ttlSeconds, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    private void requireType(Claims claims, String expected) {
        String actual = claims.get(CLAIM_TYPE, String.class);
        if (!expected.equals(actual)) {
            log.debug("[JWT] 토큰 용도 불일치: expected={}, actual={}", expected, actual);
            throw new BusinessException(CommonErrorCode.INVALID_CREDENTIALS);
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(CommonErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] 토큰 파싱 실패: {}", e.getMessage());
            throw new BusinessException(CommonErrorCode.INVALID_CREDENTIALS);
        }
    }
}
