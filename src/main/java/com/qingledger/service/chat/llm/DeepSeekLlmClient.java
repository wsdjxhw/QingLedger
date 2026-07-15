package com.qingledger.service.chat.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.qingledger.common.BusinessException;
import com.qingledger.service.chat.llm.dto.ChatCompletionRequest;
import com.qingledger.service.chat.llm.dto.ChatCompletionResponse;
import com.qingledger.service.chat.llm.dto.LlmMessage;
import com.qingledger.service.chat.llm.dto.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/** DeepSeek API 的 LLM 客户端实现 */
@Service
public class DeepSeekLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmClient.class);

    private final WebClient webClient;
    private final String model;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    public DeepSeekLlmClient(@Value("${llm.deepseek.api-key}") String apiKey,
                             @Value("${llm.deepseek.base-url:https://api.deepseek.com/v1}") String baseUrl,
                             @Value("${llm.deepseek.model:deepseek-chat}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public ChatCompletionResponse chat(String systemPrompt, List<LlmMessage> messages, List<ToolDefinition> tools) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .temperature(0.7)
                .maxTokens(2048)
                .build();

        if (tools != null && !tools.isEmpty()) {
            request.setTools(tools);
            request.setToolChoice("auto");
            // deepseek-v4-flash 默认启用思考模式，思考模式下工具调用需正确传回 reasoning_content，
            // 否则后续轮次会返回 400。这里显式禁用思考模式以兼容工具调用。
            request.setThinking(new ChatCompletionRequest.Thinking("disabled"));
        }

        LlmMessage systemMsg = LlmMessage.builder()
                .role("system")
                .content(systemPrompt)
                .build();

        List<LlmMessage> allMessages = new ArrayList<>();
        allMessages.add(systemMsg);
        if (messages != null && !messages.isEmpty()) {
            allMessages.addAll(messages);
        }
        request.setMessages(allMessages);

        try {
            return doChat(request, false);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败: {}", e.getMessage(), e);
            throw new BusinessException(500, "AI 服务暂时不可用");
        }
    }

    /**
     * 执行聊天请求
     *
     * @param retried 是否已重试过（避免无限递归）
     */
    private ChatCompletionResponse doChat(ChatCompletionRequest request, boolean retried) {
        if (log.isDebugEnabled()) {
            try {
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
                log.debug("DeepSeek API 请求:\n{}", json);
            } catch (JsonProcessingException e) {
                log.debug("DeepSeek API 请求序列化失败: {}", e.getMessage());
            }
        }

        try {
            ChatCompletionResponse response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatCompletionResponse.class)
                    .block(Duration.ofSeconds(30));

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                if (!retried) {
                    return doChat(request, true);
                }
                throw new BusinessException(500, "AI 回复异常，请重试");
            }
            return response;
        } catch (RuntimeException e) {
            if (e instanceof WebClientResponseException wcre) {
                String body = wcre.getResponseBodyAsString();
                log.error("DeepSeek API 返回错误 [status={}, body=\"{}\"]",
                        wcre.getStatusCode(), body, wcre);
            }

            if (isTimeoutError(e)) {
                throw new BusinessException(500, "AI 暂时无响应，请稍后再试");
            }
            if (!retried && isFormatError(e)) {
                return doChat(request, true);
            }
            if (isFormatError(e)) {
                throw new BusinessException(500, "AI 回复异常，请重试");
            }
            throw e;
        }
    }

    /** 判断是否为超时异常 */
    private boolean isTimeoutError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
            if (current instanceof WebClientRequestException && current.getCause() instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 判断是否为 JSON 格式解析异常（通过异常类名判断） */
    private boolean isFormatError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("DecodingException") || name.contains("JsonProcessingException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
