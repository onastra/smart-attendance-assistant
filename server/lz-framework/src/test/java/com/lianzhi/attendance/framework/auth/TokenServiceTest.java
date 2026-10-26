package com.lianzhi.attendance.framework.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 令牌服务的单元测试。
 *
 * <p>令牌是整套鉴权的入口：一旦"过期还能用""换密钥还能用""随手改一位还能用"，
 * 后面所有权限判定都形同虚设。因此这里不只测"正常能解析"，更测四类必须被拒绝的令牌。</p>
 */
class TokenServiceTest {

    /** HS256 要求密钥不少于 256 bit，即 32 字节 */
    private static final String SECRET = "unit-test-jwt-secret-please-do-not-use-in-prod-0123456789";
    private static final String OTHER_SECRET = "another-unit-test-jwt-secret-also-not-for-prod-9876543210";

    private final TokenService tokenService = new TokenService(SECRET, 7200);

    @Test
    @DisplayName("签发后解析应还原用户 ID、用户名与员工 ID")
    void generatedTokenCarriesIdentity() {
        String token = tokenService.generateToken(42L, "emp01", 7L);

        Optional<AuthUser> parsed = tokenService.parse(token);

        assertTrue(parsed.isPresent());
        assertEquals(42L, parsed.get().getUserId());
        assertEquals("emp01", parsed.get().getUsername());
        assertEquals(7L, parsed.get().getEmployeeId());
    }

    @Test
    @DisplayName("未建档用户（employeeId 为空）签发的令牌解析后仍为空，不伪造 0")
    void nullEmployeeIdStaysNull() {
        String token = tokenService.generateToken(43L, "admin", null);

        AuthUser user = tokenService.parse(token).orElseThrow();

        assertNull(user.getEmployeeId(), "没有员工档案时不得退化成 0，否则会命中错误的数据范围");
    }

    @Test
    @DisplayName("过期令牌必须解析失败，绝不放行")
    void expiredTokenIsRejected() {
        TokenService expiredIssuer = new TokenService(SECRET, -10);
        String token = expiredIssuer.generateToken(1L, "emp01", 1L);

        assertTrue(tokenService.parse(token).isEmpty(), "过期令牌必须拒绝");
    }

    @Test
    @DisplayName("被改过任一字符的令牌必须解析失败")
    void tamperedTokenIsRejected() {
        String token = tokenService.generateToken(1L, "emp01", 1L);
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("A") ? "B" : "A")
                + token.substring(token.length() - 1);

        assertTrue(tokenService.parse(tampered).isEmpty(), "签名不符的令牌必须拒绝");
    }

    @Test
    @DisplayName("用另一把密钥签发的令牌必须解析失败（密钥轮换后旧令牌失效）")
    void tokenSignedByOtherKeyIsRejected() {
        TokenService other = new TokenService(OTHER_SECRET, 7200);
        String token = other.generateToken(1L, "emp01", 1L);

        assertTrue(tokenService.parse(token).isEmpty());
    }

    @Test
    @DisplayName("空、空白、乱码令牌返回 empty，不抛异常")
    void malformedTokenReturnsEmpty() {
        assertTrue(tokenService.parse(null).isEmpty());
        assertTrue(tokenService.parse("").isEmpty());
        assertTrue(tokenService.parse("   ").isEmpty());
        assertTrue(tokenService.parse("not-a-jwt").isEmpty());
        assertTrue(tokenService.parse("a.b.c").isEmpty());
    }

    @Test
    @DisplayName("密钥长度不足 32 字节时构造即失败，不拖到运行时才炸")
    void shortSecretIsRejectedAtConstruction() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new TokenService("too-short", 7200));

        assertTrue(ex.getMessage().contains("32 字节"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("过期时间配置被如实保留")
    void expireSecondsIsExposed() {
        assertEquals(7200, tokenService.getExpireSeconds());
        assertEquals(600, new TokenService(SECRET, 600).getExpireSeconds());
    }

    @Test
    @DisplayName("同一用户两次签发的令牌都可解析（令牌不因重复签发而失效）")
    void repeatedIssuanceIsValid() {
        String first = tokenService.generateToken(5L, "emp02", 2L);
        String second = tokenService.generateToken(5L, "emp02", 2L);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(tokenService.parse(first).isPresent());
        assertTrue(tokenService.parse(second).isPresent());
    }
}
