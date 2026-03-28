package com.qingledger.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "账号(手机号/邮箱)")
    @NotBlank(message = "账号不能为空")
    private String account;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "验证码")
    private String code;
}
