package com.qingledger.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "访问Token")
    private String accessToken;

    @Schema(description = "刷新Token(仅 Mobile 端返回,Web 端通过 HttpOnly Cookie 传输)")
    private String refreshToken;

    @Schema(description = "过期时间(秒)")
    private Integer expireIn;

    @Schema(description = "用户信息")
    private UserInfo user;
}
