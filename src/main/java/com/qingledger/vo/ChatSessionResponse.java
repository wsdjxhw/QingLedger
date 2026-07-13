package com.qingledger.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionResponse {

    private Long id;

    /** 关联账本 ID */
    private Long ledgerId;

    /** 当前人设编码 */
    private String personaType;

    /** 会话状态 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
