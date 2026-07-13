package com.qingledger.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageResponse {

    private Long id;

    /** 客户端请求幂等 ID */
    private String clientRequestId;

    /** 角色：user / assistant / tool */
    private String role;

    /** 消息内容 */
    private String content;

    /** 消息类型：text / transaction / analysis / tool_call / tool_result */
    private String msgType;

    /** 关联的交易 ID */
    private Long transactionId;

    /** LLM tool_call_id */
    private String toolCallId;

    /** 工具名称 */
    private String toolName;

    /** assistant 原始 tool_calls JSON */
    private String toolCallsJson;

    /** 工具执行结果 JSON */
    private String toolResult;

    /** 工具执行状态 */
    private String toolStatus;

    private LocalDateTime createdAt;
}
