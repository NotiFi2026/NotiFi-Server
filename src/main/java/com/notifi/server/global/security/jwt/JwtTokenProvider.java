package com.notifi.server.global.security.jwt;

import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.ErrorCode;
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

    private final SecretKey signingKey;
    private final long accessTtl;    // seconds
    private final long refreshTtl;   // seconds

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-ttl}") long accessTtl,
            @Value("${jwt.refresh-token-ttl}") long refreshTtl
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String createAccessToken(Long userId, String role) {
        return buildToken(userId, role, accessTtl);
    }

    public String createRefreshToken(Long userId, String role) {
        return buildToken(userId, role, refreshTtl);
    }

    /**
     * 토큰에서 Authentication 추출.
     * 만료 → TOKEN_EXPIRED, 위조/형식 오류 → INVALID_CREDENTIALS.
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Long userId = Long.parseLong(claims.getSubject());
        String role  = claims.get("role", String.class);
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    // ── private ───────────────────────────────────────────────────────────

    private String buildToken(Long userId, String role, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] 토큰 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
    }
}
