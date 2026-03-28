package com.qingledger.service.auth.impl;

import com.qingledger.config.JwtConfig;
import com.qingledger.exception.AuthException;
import com.qingledger.service.auth.TokenService;
import com.qingledger.utils.JwtUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TokenServiceImpl implements TokenService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;
    private final RedisTemplate<String, Object> redisTemplate;

    public TokenServiceImpl(JwtUtil jwtUtil, JwtConfig jwtConfig,
                           RedisTemplate<String, Object> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.jwtConfig = jwtConfig;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public TokenPairResult generateTokens(Long userId) {
        String accessToken = jwtUtil.generateAccessToken(userId);
        JwtUtil.RefreshTokenResult refreshResult = jwtUtil.generateRefreshToken(userId);
        String refreshToken = refreshResult.token();
        String tokenId = refreshResult.tokenId();

        // 存储Refresh Token到Redis
        String key = getRefreshTokenKey(userId, tokenId);
        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("userId", userId);
        deviceInfo.put("tokenId", tokenId);
        redisTemplate.opsForValue().set(key, deviceInfo, REFRESH_TOKEN_TTL);

        int expireIn = jwtConfig.getAccessTokenExpire().intValue();

        return new TokenPairResult(accessToken, refreshToken, expireIn);
    }

    @Override
    public TokenPairResult refreshAccessToken(String refreshToken) {
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new AuthException(1009, "Refresh Token无效");
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        String tokenId = jwtUtil.getTokenId(refreshToken);

        // 检查Refresh Token是否存在
        String key = getRefreshTokenKey(userId, tokenId);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            throw new AuthException(1009, "Refresh Token无效或已过期");
        }

        // 废除旧的Refresh Token
        redisTemplate.delete(key);

        // 生成新的Token对
        return generateTokens(userId);
    }

    @Override
    public void revokeRefreshToken(Long userId, String tokenId) {
        String key = getRefreshTokenKey(userId, tokenId);
        redisTemplate.delete(key);
    }

    @Override
    public Long parseAccessToken(String accessToken) {
        if (!jwtUtil.isAccessToken(accessToken)) {
            throw new AuthException(1008, "Token类型错误");
        }

        try {
            return jwtUtil.getUserId(accessToken);
        } catch (Exception e) {
            throw new AuthException(1008, "Token无效或已过期: " + e.getMessage());
        }
    }

    private String getRefreshTokenKey(Long userId, String tokenId) {
        return "refresh_token:" + userId + ":" + tokenId;
    }
}