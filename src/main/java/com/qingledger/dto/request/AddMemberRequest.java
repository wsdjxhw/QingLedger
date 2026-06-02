package com.qingledger.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddMemberRequest {

    @NotBlank(message = "手机号或邮箱不能为空")
    private String identifier;
}
