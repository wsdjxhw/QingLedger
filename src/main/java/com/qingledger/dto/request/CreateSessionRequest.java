package com.qingledger.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionRequest {

    /** 关联账本 ID */
    @NotNull(message = "账本ID不能为空")
    @Positive(message = "账本ID必须大于0")
    private Long ledgerId;

    /** 人设编码 */
    @NotBlank(message = "人设类型不能为空")
    @Size(max = 20, message = "人设类型长度不能超过20")
    private String personaType;

    /** 会话标题（可选，为空则由首条消息自动生成） */
    @Size(max = 100, message = "会话标题长度不能超过100")
    private String title;
}
