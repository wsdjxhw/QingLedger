package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID */
    private Long sessionId;

    /** 角色：user / assistant / system / tool */
    private String role;

    /** 消息内容 */
    private String content;

    /** 消息类型：text / transaction / analysis / tool_call / tool_result */
    private String msgType;

    /** 关联的交易 ID（仅 transaction 类型有值） */
    private Long transactionId;

    /** 消耗的 token 数 */
    private Integer tokensUsed;

    /** 客户端请求幂等 ID */
    private String clientRequestId;

    /** 消息去重键 */
    private String dedupeKey;

    /** assistant 原始 tool_calls JSON */
    private String toolCallsJson;

    /** LLM tool_call_id */
    private String toolCallId;

    /** 工具名称 */
    private String toolName;

    /** 工具参数 JSON */
    private String toolArguments;

    /** 工具执行结果 JSON */
    private String toolResult;

    /** 工具执行状态：success / error */
    private String toolStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
