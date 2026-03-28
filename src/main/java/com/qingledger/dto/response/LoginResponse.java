package com.qingledger.dto.response;

import lombok.Data;

/**
 * 登录响应
 */
@Data
public class LoginResponse {
    /**
     * 访问令牌
     */
    private String token;
}
