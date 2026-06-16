package com.qingledger.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinByInvitationRequest {

    @NotBlank(message = "邀请码不能为空")
    @Size(max = 12, message = "邀请码格式不正确")
    private String code;
}
