package com.qingledger.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "解绑登录方式请求")
public class UnbindRequest {

    @Schema(description = "认证类型(PHONE/EMAIL)")
    @NotBlank(message = "认证类型不能为空")
    @Pattern(regexp = "^(PHONE|EMAIL)$", message = "认证类型必须是 PHONE 或 EMAIL")
    private String authType;

    @Schema(description = "用户当前密码")
    @NotBlank(message = "密码不能为空")
    private String password;
}
