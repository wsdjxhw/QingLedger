package com.qingledger.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "绑定信息")
public class BindingInfo {

    @Schema(description = "类型: phone/email")
    private String type;

    @Schema(description = "标识符(脱敏)")
    private String identifier;

    @Schema(description = "是否为主要登录方式")
    private Boolean isPrimary;

    @Schema(description = "是否已验证")
    private Boolean verified;

    @Schema(description = "绑定时间")
    private LocalDateTime bindAt;
}
