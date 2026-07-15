package com.qingledger.service.chat.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** LLM Chat Completion 响应（对应 API 的 response body） */
@Data
@NoArgsConstructor
public class ChatCompletionResponse {

    /** 响应 ID */
    private String id;

    /** 对象类型 */
    private String object;

    /** 创建时间戳 */
    private Long created;

    /** 使用的模型 */
    private String model;

    /** 回复选项列表 */
    private List<Choice> choices;

    /** Token 用量 */
    private Usage usage;

    /** 单个回复选项 */
    @Data
    @NoArgsConstructor
    public static class Choice {
        /** 选项索引 */
        private Integer index;
        /** 回复消息 */
        private LlmMessage message;
        /** 结束原因：stop / length / tool_calls / content_filter */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /** Token 用量统计 */
    @Data
    @NoArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
