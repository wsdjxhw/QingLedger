package com.qingledger.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 助手回复内容 */
    private String reply;

    /** 关联的交易 ID（如本次消息创建了交易） */
    private Long transactionId;

    /** 消息类型：text / transaction */
    private String msgType;
}
