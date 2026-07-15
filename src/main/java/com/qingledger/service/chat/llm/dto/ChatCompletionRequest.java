package com.qingledger.service.chat.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** LLM Chat Completion 请求（对应 API 的 request body） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionRequest {

    /** 模型名称 */
    private String model;

    /** 消息列表 */
    private List<LlmMessage> messages;

    /** 工具定义列表 */
    private List<ToolDefinition> tools;

    /** 工具选择策略：auto / none / required / {"type":"function","function":{"name":"..."}} */
    @JsonProperty("tool_choice")
    private String toolChoice;

    /** 生成温度 */
    private Double temperature;

    /** 最大输出 token 数 */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** 思考模式控制：deepseek-v4-flash 默认启用思考模式，工具调用时需禁用 */
    private Thinking thinking;

    /** 思考模式控制参数 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Thinking {
        /** enabled / disabled */
        private String type;
    }
}
