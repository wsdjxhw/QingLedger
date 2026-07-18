package com.qingledger.service.chat.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** LLM 工具调用（对应 API tool_calls 数组中的元素） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 工具调用 ID */
    private String id;

    /** 类型，固定为 "function" */
    private String type;

    /** 函数调用详情 */
    private FunctionCall function;

    /** 函数调用 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        /** 函数名称 */
        private String name;
        /** 参数 JSON */
        private String arguments;
    }
}
