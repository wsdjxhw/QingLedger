package com.qingledger.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 65535, message = "消息内容长度不能超过65535")
    private String content;

    /** 客户端请求幂等 ID（UUID v4 格式，session 内唯一） */
    @NotBlank(message = "clientRequestId 不能为空")
    @Size(max = 36, message = "clientRequestId 长度不能超过36")
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
            message = "clientRequestId 格式不正确"
    )
    private String clientRequestId;
}
