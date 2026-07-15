package com.qingledger.service.chat.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** LLM 聊天消息（对应 API 的 message 对象） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessage {

    /** 角色：system / user / assistant / tool */
    private String role;

    /** 消息内容 */
    private String content;

    /** tool 消息中关联的 tool_call_id */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /** assistant 消息中的工具调用列表 */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
}
