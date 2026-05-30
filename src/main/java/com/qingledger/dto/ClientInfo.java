package com.qingledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户端信息
 */
@Data
@Schema(description = "客户端信息")
public class ClientInfo {
    @Schema(description = "客户端类型", example = "WEB", allowableValues = {"WEB", "MOBILE"})
    /**
     * 客户端类型: WEB, MOBILE
     */
    private String clientType;

    @Schema(description = "设备指纹(Mobile: 设备ID, Web: IP段+UserAgent)")
    /**
     * 设备指纹(Mobile: 设备ID, Web: IP段+UserAgent)
     */
    private String deviceFingerprint;

    @Schema(description = "IP 地址")
    /**
     * IP 地址
     */
    private String ipAddress;

    @Schema(description = "User-Agent")
    /**
     * User-Agent
     */
    private String userAgent;

    @Schema(description = "城市(IP 归属地)")
    /**
     * 城市(IP 归属地)
     */
    private String city;
}