package com.qingledger.service.chat.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** LLM 工具定义（对应 API tools 数组中的元素） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /** 类型，固定为 "function" */
    private String type;

    /** 函数定义 */
    private Function function;

    /** 函数定义 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        /** 函数名称 */
        private String name;
        /** 函数描述 */
        private String description;
        /** 参数定义 */
        private Parameters parameters;
    }

    /** 参数定义 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameters {
        /** 参数类型，固定为 "object" */
        private String type;
        /** 属性定义 */
        private Map<String, Property> properties;
        /** 必填属性列表 */
        private List<String> required;
    }

    /** 单个属性定义 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Property {
        /** 属性类型 */
        private String type;
        /** 属性描述 */
        private String description;
        /** 枚举值列表 */
        @JsonProperty("enum")
        private List<String> enum_;
    }
}
