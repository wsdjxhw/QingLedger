package com.qingledger.service.auth;

public interface TokenService {
    TokenPairResult generateTokens(Long userId);
    TokenPairResult refreshAccessToken(String refreshToken);
    void revokeRefreshToken(Long userId, String tokenId);
    Long parseAccessToken(String accessToken);

    record TokenPairResult(String accessToken, String refreshToken, Integer expireIn) {
    }
}