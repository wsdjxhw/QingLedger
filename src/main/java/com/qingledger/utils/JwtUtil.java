package com.qingledger.utils;

import com.qingledger.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    private final JwtConfig jwtConfig;

    public JwtUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public String generateAccessToken(Long userId) {
        return generateToken(userId, "access", jwtConfig.getAccessTokenExpire());
    }

    public RefreshTokenResult generateRefreshToken(Long userId) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token = generateToken(userId, "refresh", jwtConfig.getRefreshTokenExpire(), tokenId);
        return new RefreshTokenResult(token, tokenId);
    }

    private String generateToken(Long userId, String type, Long expireSeconds) {
        return generateToken(userId, type, expireSeconds, null);
    }

    private String generateToken(Long userId, String type, Long expireSeconds, String tokenId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", type);
        if (tokenId != null) {
            claims.put("tokenId", tokenId);
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    public String getTokenType(String token) {
        return parseToken(token).get("type", String.class);
    }

    public String getTokenId(String token) {
        return parseToken(token).get("tokenId", String.class);
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public record RefreshTokenResult(String token, String tokenId) {
    }
}
