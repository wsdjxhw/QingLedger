package com.qingledger.service.chat.llm;

import com.qingledger.service.chat.llm.dto.ChatCompletionResponse;
import com.qingledger.service.chat.llm.dto.LlmMessage;
import com.qingledger.service.chat.llm.dto.ToolDefinition;

import java.util.List;

/** LLM 聊天接口抽象 */
public interface LlmClient {

    /** 发送聊天请求（system prompt + 历史消息 + 工具定义），返回 LLM 回复 */
    ChatCompletionResponse chat(String systemPrompt,
                                List<LlmMessage> messages,
                                List<ToolDefinition> tools);
}
