package com.qingledger.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "发送验证码请求")
public class SendCodeRequest {

    @Schema(description = "类型: register/login/bind/reset")
    @NotBlank(message = "类型不能为空")
    @Pattern(regexp = "(?i)register|login|bind|reset|REGISTER|LOGIN|BIND|RESET_PASSWORD", message = "类型无效")
    private String type;

    @Schema(description = "手机号或邮箱")
    @NotBlank(message = "目标不能为空")
    private String target;
}
