package com.qingledger.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * RefreshToken 信息(用于 Redis 存储)
 */
@Data
public class RefreshTokenInfo {
    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * Token ID
     */
    private String tokenId;

    /**
     * 客户端类型: WEB, MOBILE
     */
    private String clientType;

    /**
     * 设备指纹
     */
    private String deviceFingerprint;

    /**
     * IP 段(前三个段,如 192.168.1)
     */
    private String ipSegment;

    /**
     * 城市
     */
    private String city;

    /**
     * User-Agent (仅 Web)
     */
    private String userAgent;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后验证时间
     */
    private LocalDateTime lastVerifiedAt;
}