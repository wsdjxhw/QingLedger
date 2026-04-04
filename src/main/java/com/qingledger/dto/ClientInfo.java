package com.qingledger.dto;

import lombok.Data;

/**
 * 客户端信息
 */
@Data
public class ClientInfo {
    /**
     * 客户端类型: WEB, MOBILE
     */
    private String clientType;

    /**
     * 设备指纹(Mobile: 设备ID, Web: IP段+UserAgent)
     */
    private String deviceFingerprint;

    /**
     * IP 地址
     */
    private String ipAddress;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 城市(IP 归属地)
     */
    private String city;
}