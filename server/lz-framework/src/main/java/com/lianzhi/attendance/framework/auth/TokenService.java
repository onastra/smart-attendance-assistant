package com.lianzhi.attendance.framework.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JWT 签发与解析。
 *
 * <p>密钥由 <code>lz.security.jwt.secret</code> 注入（建议通过环境变量 LZ_JWT_SECRET 提供），
 * 严禁把正式密钥提交进代码库。</p>
 */
@Slf4j
@Component
public class TokenService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_EMPLOYEE_ID = "employeeId";

    private final SecretKey key;
    private final long expireSeconds;

    public TokenService(@Value("${lz.security.jwt.secret}") String secret,
                        @Value("${lz.security.jwt.expire-seconds:7200}") long expireSeconds) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // 明确失败：HS256 要求密钥长度不少于 256 bit，否则运行期才会抛异常，排查成本高
            throw new IllegalStateException("JWT 密钥长度不足 32 字节，请通过 LZ_JWT_SECRET 配置更强的密钥");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expireSeconds = expireSeconds;
    }

    public String generateToken(Long userId, String username, Long employeeId) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USERNAME, username);
        if (employeeId != null) {
            claims.put(CLAIM_EMPLOYEE_ID, employeeId);
        }
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌。任何异常（过期、签名不符、格式错误）统一返回 empty，由调用方决定是否拒绝访问。
     */
    public Optional<AuthUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            AuthUser user = new AuthUser();
            user.setUserId(Long.valueOf(claims.getSubject()));
            user.setUsername(claims.get(CLAIM_USERNAME, String.class));
            Object employeeId = claims.get(CLAIM_EMPLOYEE_ID);
            if (employeeId instanceof Integer intValue) {
                user.setEmployeeId(intValue.longValue());
            } else if (employeeId instanceof Long longValue) {
                user.setEmployeeId(longValue);
            }
            return Optional.of(user);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
