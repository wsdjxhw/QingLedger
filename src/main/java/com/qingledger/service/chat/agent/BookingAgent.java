package com.qingledger.service.chat.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingledger.common.BusinessException;
import com.qingledger.dto.request.CreateTransactionRequest;
import com.qingledger.entity.Category;
import com.qingledger.entity.ChatMessage;
import com.qingledger.entity.ChatToolExecution;
import com.qingledger.enums.CategoryType;
import com.qingledger.enums.TransactionType;
import com.qingledger.mapper.ChatMessageMapper;
import com.qingledger.mapper.ChatRequestMapper;
import com.qingledger.mapper.ChatSessionMapper;
import com.qingledger.mapper.ChatToolExecutionMapper;
import com.qingledger.service.category.CategoryService;
import com.qingledger.service.chat.llm.LlmClient;
import com.qingledger.service.chat.llm.dto.ChatCompletionResponse;
import com.qingledger.service.chat.llm.dto.LlmMessage;
import com.qingledger.service.chat.llm.dto.ToolCall;
import com.qingledger.service.chat.llm.dto.ToolDefinition;
import com.qingledger.service.transaction.TransactionService;
import com.qingledger.vo.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * AI 记账 Agent
 *
 * <p>核心职责：
 * <ol>
 *   <li>构建 LLM 对话上下文（含历史压缩）</li>
 *   <li>多轮工具调用循环（最多 3 轮）</li>
 *   <li>幂等执行记账工具（create_transaction / list_categories）</li>
 *   <li>租约心跳保活</li>
 * </ol>
 */
@Component
public class BookingAgent {

    private static final Logger log = LoggerFactory.getLogger(BookingAgent.class);
    private static final int MAX_TOOL_ROUNDS = 3;
    private static final int TOKEN_BUDGET = 4000;
    private static final int TOOL_DEFINITION_BUDGET = 500;
    private static final long TOOL_EXECUTION_STALE_SECONDS = 300;
    private static final long LEASE_HEARTBEAT_SECONDS = 60;

    private final LlmClient llmClient;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final ChatToolExecutionMapper toolExecutionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatRequestMapper chatRequestMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public BookingAgent(LlmClient llmClient,
                        TransactionService transactionService,
                        CategoryService categoryService,
                        ChatToolExecutionMapper toolExecutionMapper,
                        ChatMessageMapper chatMessageMapper,
                        ChatRequestMapper chatRequestMapper,
                        ChatSessionMapper chatSessionMapper,
                        ObjectMapper objectMapper,
                        TransactionTemplate transactionTemplate) {
        this.llmClient = llmClient;
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.toolExecutionMapper = toolExecutionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatRequestMapper = chatRequestMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 执行 AI 记账请求
     *
     * <p>流程：
     * <ol>
     *   <li>构建 LLM 消息列表（含历史压缩）</li>
     *   <li>多轮循环：调用 LLM → 处理工具调用 → 重复</li>
     *   <li>每轮调用前后检查租约所有权</li>
     *   <li>最终回复包含交易 ID（如已创建）</li>
     * </ol>
     */
    public ChatResponse run(Long requestId,
                            String leaseToken,
                            Long sessionId,
                            String clientRequestId,
                            String userMessage,
                            List<ChatMessage> history,
                            String personaPrompt,
                            Long ledgerId,
                            Long userId) {
        List<LlmMessage> messages = buildMessages(personaPrompt, history, userMessage);
        List<ToolDefinition> tools = buildToolDefinitions();
        Long createdTransactionId = null;

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            assertRequestOwnership(sessionId, requestId, leaseToken);
            ChatCompletionResponse response = executeWithLeaseHeartbeat(
                    sessionId,
                    requestId,
                    leaseToken,
                    null,
                    () -> llmClient.chat(personaPrompt, messages, tools)
            );
            assertRequestOwnership(sessionId, requestId, leaseToken);

            ChatCompletionResponse.Choice choice = response.getChoices().get(0);
            LlmMessage reply = choice.getMessage();
            String finishReason = choice.getFinishReason();
            Integer tokensUsed = response.getUsage() == null ? null : response.getUsage().getTotalTokens();

            if ("tool_calls".equals(finishReason) && reply != null && reply.getToolCalls() != null && !reply.getToolCalls().isEmpty()) {
                ChatMessage toolCallMsg = new ChatMessage();
                toolCallMsg.setSessionId(sessionId);
                toolCallMsg.setRole("assistant");
                toolCallMsg.setMsgType("tool_call");
                toolCallMsg.setContent(nullToEmpty(reply.getContent()));
                toolCallMsg.setTokensUsed(tokensUsed);
                toolCallMsg.setClientRequestId(clientRequestId);
                toolCallMsg.setToolCallsJson(toJsonString(reply.getToolCalls()));
                chatMessageMapper.insert(toolCallMsg);

                messages.add(reply);

                for (ToolCall toolCall : reply.getToolCalls()) {
                    if (isCreateTransactionCall(toolCall) && createdTransactionId != null) {
                        throw new BusinessException(500, "一次对话请求只允许创建一笔交易");
                    }

                    ToolExecutionResult execution = executeTool(
                            toolCall,
                            requestId,
                            leaseToken,
                            sessionId,
                            clientRequestId,
                            ledgerId,
                            userId
                    );
                    if (!execution.success()) {
                        throw new BusinessException(500, "工具执行未完成，请稍后重试");
                    }
                    if (execution.transactionId() != null) {
                        createdTransactionId = execution.transactionId();
                    }

                    messages.add(LlmMessage.builder()
                            .role("tool")
                            .toolCallId(toolCall.getId())
                            .content(execution.resultJson())
                            .build());
                }
                continue;
            }

            assertRequestOwnership(sessionId, requestId, leaseToken);
            String replyContent = reply == null ? "" : nullToEmpty(reply.getContent());
            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(replyContent);
            assistantMsg.setMsgType(createdTransactionId != null ? "transaction" : "text");
            assistantMsg.setTransactionId(createdTransactionId);
            assistantMsg.setTokensUsed(tokensUsed);
            assistantMsg.setClientRequestId(clientRequestId);
            assistantMsg.setDedupeKey(buildAssistantMessageDedupeKey(sessionId, clientRequestId));
            insertMessageIgnoreDuplicate(assistantMsg);

            return new ChatResponse(replyContent, createdTransactionId, createdTransactionId != null ? "transaction" : "text");
        }

        throw new BusinessException(500, "处理超时，请重试");
    }

    /**
     * 执行单个工具调用
     *
     * <p>幂等保障流程：
     * <ol>
     *   <li>通过 idempotentKey 查找或创建执行记录</li>
     *   <li>已成功则直接返回缓存结果</li>
     *   <li>未完成则加锁执行（事务内）</li>
     *   <li>失败时标记 error</li>
     * </ol>
     */
    private ToolExecutionResult executeTool(ToolCall toolCall,
                                            Long requestId,
                                            String leaseToken,
                                            Long sessionId,
                                            String clientRequestId,
                                            Long ledgerId,
                                            Long userId) {
        assertRequestOwnership(sessionId, requestId, leaseToken);

        String toolName = toolCall == null || toolCall.getFunction() == null ? null : toolCall.getFunction().getName();
        String arguments = toolCall == null || toolCall.getFunction() == null ? null : toolCall.getFunction().getArguments();
        String normalizedArgs = normalizeJson(arguments);
        String argsHash = sha256(normalizedArgs);
        String idempotentKey = requestId + ":" + toolName + ":" + argsHash;

        ChatToolExecution execution = acquireExecution(requestId, toolName, argsHash, idempotentKey);
        if (execution == null) {
            return new ToolExecutionResult(errorJson("操作冲突，请重试"), null, false);
        }

        if ("success".equals(execution.getStatus())) {
            ensureToolResultMessagePersisted(
                    sessionId,
                    clientRequestId,
                    toolCall,
                    normalizedArgs,
                    resultDedupeKey(sessionId, clientRequestId, toolName, argsHash),
                    execution.getResultJson(),
                    "success",
                    execution.getTransactionId()
            );
            return new ToolExecutionResult(execution.getResultJson(), execution.getTransactionId(), true);
        }

        try {
            ToolExecutionResult result = executeWithLeaseHeartbeat(
                    sessionId,
                    requestId,
                    leaseToken,
                    execution,
                    () -> transactionTemplate.execute(status -> {
                        assertRequestOwnership(sessionId, requestId, leaseToken);
                        touchProcessingExecution(execution);

                        String resultJson;
                        Long transactionId = null;
                        if ("create_transaction".equals(toolName)) {
                            transactionId = doCreateTransaction(arguments, ledgerId, userId);
                            resultJson = toJsonString(Map.of("transactionId", transactionId));
                        } else if ("list_categories".equals(toolName)) {
                            resultJson = doListCategories(arguments, userId);
                        } else {
                            resultJson = errorJson("未知工具: " + nullToEmpty(toolName));
                        }

                        int rows = toolExecutionMapper.markSuccess(
                                execution.getId(),
                                execution.getLeaseToken(),
                                resultJson,
                                transactionId
                        );
                        if (rows <= 0) {
                            throw new BusinessException(409, "工具执行所有权已丢失");
                        }

                        ensureToolResultMessagePersisted(
                                sessionId,
                                clientRequestId,
                                toolCall,
                                normalizedArgs,
                                resultDedupeKey(sessionId, clientRequestId, toolName, argsHash),
                                resultJson,
                                "success",
                                transactionId
                        );
                        assertRequestOwnership(sessionId, requestId, leaseToken);
                        return new ToolExecutionResult(resultJson, transactionId, true);
                    })
            );

            if (result == null) {
                throw new BusinessException(500, "工具执行未完成，请稍后重试");
            }
            return result;
        } catch (Exception e) {
            log.error("工具执行失败: tool={}, args={}", toolName, arguments, e);
            String errorResult = errorJson(e.getMessage());
            int rows = toolExecutionMapper.markError(execution.getId(), execution.getLeaseToken(), errorResult);
            if (rows <= 0) {
                log.warn("markError skipped because tool execution lease was lost: executionId={}", execution.getId());
            }
            return new ToolExecutionResult(errorResult, null, false);
        }
    }

    /**
     * 获取工具执行记录（幂等）
     *
     * <p>不存在则新建；已存在则尝试认领（失败时返回 null）。
     */
    private ChatToolExecution acquireExecution(Long requestId,
                                               String toolName,
                                               String argsHash,
                                               String idempotentKey) {
        ChatToolExecution existing = toolExecutionMapper.selectByIdempotentKey(idempotentKey);
        if (existing != null) {
            return claimExistingExecution(existing);
        }

        ChatToolExecution execution = new ChatToolExecution();
        execution.setRequestId(requestId);
        execution.setToolName(toolName);
        execution.setArgumentsHash(argsHash);
        execution.setIdempotentKey(idempotentKey);
        execution.setLeaseToken(UUID.randomUUID().toString());
        execution.setStatus("processing");

        try {
            toolExecutionMapper.insert(execution);
            return execution;
        } catch (Exception e) {
            ChatToolExecution inserted = toolExecutionMapper.selectByIdempotentKey(idempotentKey);
            if (inserted != null) {
                return claimExistingExecution(inserted);
            }
            throw e;
        }
    }

    /**
     * 认领已存在的工具执行记录
     *
     * <ul>
     *   <li>成功 → 直接返回</li>
     *   <li>失败 → 尝试重试（重新设为 processing）</li>
     *   <li>处理中且已超时 → 抢锁</li>
     *   <li>处理中但未超时 → 返回 null</li>
     * </ul>
     */
    private ChatToolExecution claimExistingExecution(ChatToolExecution existing) {
        if ("success".equals(existing.getStatus())) {
            return existing;
        }

        String newLeaseToken = UUID.randomUUID().toString();
        if ("error".equals(existing.getStatus())) {
            if (toolExecutionMapper.retryErroredExecution(existing.getId(), newLeaseToken) <= 0) {
                return null;
            }
            existing.setStatus("processing");
            existing.setLeaseToken(newLeaseToken);
            existing.setResultJson(null);
            existing.setTransactionId(null);
            existing.setUpdatedAt(LocalDateTime.now());
            return existing;
        }

        if (!tryClaimStaleExecution(existing, newLeaseToken)) {
            return null;
        }
        existing.setStatus("processing");
        existing.setLeaseToken(newLeaseToken);
        existing.setUpdatedAt(LocalDateTime.now());
        return existing;
    }

    /** 创建交易 */
    private Long doCreateTransaction(String argumentsJson, Long ledgerId, Long userId) {
        Map<String, Object> args = parseJsonMap(argumentsJson);
        String type = args.get("type") == null ? null : args.get("type").toString();
        Object amountObj = args.get("amount");
        String categoryName = args.get("category_name") == null ? null : args.get("category_name").toString();
        String note = args.get("note") == null ? null : args.get("note").toString();
        String occurDate = args.get("occur_date") == null ? null : args.get("occur_date").toString();

        if (type == null || amountObj == null) {
            throw new BusinessException(400, "工具参数不完整");
        }

        TransactionType txnType = TransactionType.fromCode(type);
        BigDecimal amount = new BigDecimal(amountObj.toString());
        Integer categoryId = resolveCategoryId(categoryName, txnType, userId);

        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setLedgerId(ledgerId);
        req.setType(txnType);
        req.setAmount(amount);
        req.setCategoryId(categoryId);
        req.setOccurDate(occurDate != null && !occurDate.isBlank()
                ? occurDate
                : LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        req.setNote(note != null ? note : "");
        return transactionService.createTransaction(userId, req);
    }

    /**
     * 根据分类名称解析分类 ID
     *
     * <p>匹配策略：
     * <ol>
     *   <li>精确名称匹配</li>
     *   <li>同义词映射匹配（如"午饭"→"餐饮"）</li>
     *   <li>模糊包含匹配</li>
     *   <li>兜底返回默认分类（sort_order=99）</li>
     * </ol>
     */
    private Integer resolveCategoryId(String categoryName, TransactionType type, Long userId) {
        if (categoryName == null || categoryName.isBlank()) {
            return getDefaultCategoryId(type, userId);
        }

        String name = categoryName.trim().toLowerCase();
        CategoryType categoryType = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        List<Category> typedCategories = categoryService.listCategories(userId, categoryType);
        if (typedCategories.isEmpty()) {
            return null;
        }

        for (Category category : typedCategories) {
            if (category.getName() != null && category.getName().equalsIgnoreCase(name)) {
                return category.getId();
            }
        }

        Map<String, String> synonyms = buildSynonymMap();
        String mapped = synonyms.get(name);
        if (mapped != null) {
            for (Category category : typedCategories) {
                if (category.getName() != null && category.getName().equalsIgnoreCase(mapped)) {
                    return category.getId();
                }
            }
        }

        for (Category category : typedCategories) {
            String categoryText = category.getName() == null ? "" : category.getName().toLowerCase();
            if (name.contains(categoryText) || categoryText.contains(name)) {
                return category.getId();
            }
        }

        return getDefaultCategoryId(type, userId);
    }

    /** 获取默认分类（sort_order=99） */
    private Integer getDefaultCategoryId(TransactionType type, Long userId) {
        CategoryType categoryType = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        List<Category> defaults = categoryService.listCategories(userId, categoryType).stream()
                .filter(category -> Objects.equals(category.getSortOrder(), 99))
                .toList();
        return defaults.isEmpty() ? null : defaults.get(0).getId();
    }

    /** 构建分类名称同义词映射 */
    private Map<String, String> buildSynonymMap() {
        Map<String, String> map = new HashMap<>();
        map.put("午饭", "餐饮");
        map.put("早饭", "餐饮");
        map.put("晚饭", "餐饮");
        map.put("吃饭", "餐饮");
        map.put("外卖", "餐饮");
        map.put("打车", "交通");
        map.put("开车", "交通");
        map.put("地铁", "交通");
        map.put("公交", "交通");
        map.put("汽油", "交通");
        map.put("加油", "交通");
        map.put("买衣服", "购物");
        map.put("衣服", "购物");
        map.put("看电影", "娱乐");
        map.put("房租", "居住");
        map.put("水电", "居住");
        map.put("工资", "工资");
        map.put("发工资", "工资");
        map.put("理财", "理财");
        map.put("投资", "理财");
        map.put("看病", "医疗");
        map.put("买书", "教育");
        map.put("学习", "教育");
        return map;
    }

    /** 列出分类（返回 JSON） */
    private String doListCategories(String argumentsJson, Long userId) {
        Map<String, Object> args = parseJsonMap(argumentsJson);
        String type = args.get("type") == null ? null : args.get("type").toString();
        CategoryType categoryType = CategoryType.fromCode(type);
        List<Category> categories = categoryService.listCategories(userId, categoryType);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Category category : categories) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", category.getId());
            item.put("name", category.getName());
            item.put("type", category.getType() == null ? null : category.getType().getCode());
            result.add(item);
        }
        return toJsonString(result);
    }

    /**
     * 构建 LLM 消息列表（含历史压缩）
     *
     * <p>按 TOKEN_BUDGET 限制从最近历史开始截取，只保留最近的工具调用链。
     */
    private List<LlmMessage> buildMessages(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<HistoryBlock> blocks = buildHistoryBlocks(history);
        List<LlmMessage> messages = new ArrayList<>();
        int estimatedTokens = estimateTokens(systemPrompt) + TOOL_DEFINITION_BUDGET + estimateTokens(userMessage);
        List<HistoryBlock> selected = new ArrayList<>();

        for (int i = blocks.size() - 1; i >= 0; i--) {
            HistoryBlock block = blocks.get(i);
            if (estimatedTokens + block.tokens() > TOKEN_BUDGET) {
                break;
            }
            selected.add(0, block);
            estimatedTokens += block.tokens();
        }

        for (HistoryBlock block : selected) {
            messages.addAll(block.messages());
        }

        messages.add(LlmMessage.builder()
                .role("user")
                .content(userMessage)
                .build());
        return messages;
    }

    /**
     * 构建历史消息块
     *
     * <p>将连续的工具调用（assistant tool_call + tool result）合并为一个块，
     * 确保工具调用链的完整性；普通的 user/assistant 消息转为单条消息块。
     */
    private List<HistoryBlock> buildHistoryBlocks(List<ChatMessage> history) {
        List<HistoryBlock> blocks = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if ("tool".equals(msg.getRole())) {
                continue;
            }

            if ("assistant".equals(msg.getRole()) && "tool_call".equals(msg.getMsgType()) && msg.getToolCallsJson() != null) {
                List<ToolCall> toolCalls = parseToolCalls(msg.getToolCallsJson());
                if (toolCalls.isEmpty()) {
                    continue;
                }

                List<LlmMessage> blockMessages = new ArrayList<>();
                blockMessages.add(LlmMessage.builder()
                        .role("assistant")
                        .content(msg.getContent() == null || msg.getContent().isBlank() ? null : msg.getContent())
                        .toolCalls(toolCalls)
                        .build());

                int blockTokens = tokenCost(msg);
                int cursor = i + 1;
                boolean complete = true;

                for (ToolCall toolCall : toolCalls) {
                    if (cursor >= history.size()) {
                        complete = false;
                        break;
                    }
                    ChatMessage toolMsg = history.get(cursor);
                    if (!"tool".equals(toolMsg.getRole())
                            || !"tool_result".equals(toolMsg.getMsgType())
                            || !Objects.equals(toolCall.getId(), toolMsg.getToolCallId())) {
                        complete = false;
                        break;
                    }
                    blockMessages.add(LlmMessage.builder()
                            .role("tool")
                            .toolCallId(toolMsg.getToolCallId())
                            .content(toolMsg.getToolResult())
                            .build());
                    blockTokens += tokenCost(toolMsg);
                    cursor++;
                }

                if (complete) {
                    blocks.add(new HistoryBlock(blockMessages, blockTokens, false, msg.getClientRequestId()));
                    i = cursor - 1;
                } else {
                    while (cursor < history.size()
                            && "tool".equals(history.get(cursor).getRole())
                            && "tool_result".equals(history.get(cursor).getMsgType())) {
                        cursor++;
                    }
                    i = cursor - 1;
                }
                continue;
            }

            LlmMessage llmMessage = toPlainLlmMessage(msg);
            if (llmMessage != null) {
                blocks.add(new HistoryBlock(List.of(llmMessage), tokenCost(msg), "user".equals(msg.getRole()), msg.getClientRequestId()));
            }
        }
        return blocks;
    }

    /** ChatMessage 转 LlmMessage（仅 user / assistant 角色） */
    private LlmMessage toPlainLlmMessage(ChatMessage msg) {
        if ("user".equals(msg.getRole())) {
            return LlmMessage.builder()
                    .role("user")
                    .content(msg.getContent())
                    .build();
        }
        if ("assistant".equals(msg.getRole())) {
            return LlmMessage.builder()
                    .role("assistant")
                    .content(msg.getContent())
                    .build();
        }
        return null;
    }

    /** 估算单条消息的 token 数 */
    private int tokenCost(ChatMessage msg) {
        if (msg.getTokensUsed() != null) {
            return msg.getTokensUsed();
        }
        int cost = estimateTokens(msg.getContent());
        if (msg.getToolCallsJson() != null) {
            cost += estimateTokens(msg.getToolCallsJson());
        }
        if (msg.getToolResult() != null) {
            cost += estimateTokens(msg.getToolResult());
        }
        return cost;
    }

    /** 判断是否为 create_transaction 调用 */
    private boolean isCreateTransactionCall(ToolCall toolCall) {
        return toolCall != null
                && toolCall.getFunction() != null
                && "create_transaction".equals(toolCall.getFunction().getName());
    }

    /** 构建工具定义列表 */
    private List<ToolDefinition> buildToolDefinitions() {
        ToolDefinition createTransaction = ToolDefinition.builder()
                .type("function")
                .function(ToolDefinition.Function.builder()
                        .name("create_transaction")
                        .description("创建一笔交易记录，当用户提到消费、收入、花费、支出时调用此工具")
                        .parameters(ToolDefinition.Parameters.builder()
                                .type("object")
                                .properties(Map.of(
                                        "type", ToolDefinition.Property.builder()
                                                .type("string")
                                                .description("交易类型: income 或 expense")
                                                .enum_(List.of("income", "expense"))
                                                .build(),
                                        "amount", ToolDefinition.Property.builder()
                                                .type("number")
                                                .description("交易金额，如 35.00")
                                                .build(),
                                        "category_name", ToolDefinition.Property.builder()
                                                .type("string")
                                                .description("分类名称，如 餐饮、交通、购物、娱乐、居住、医疗、教育、工资、理财")
                                                .build(),
                                        "note", ToolDefinition.Property.builder()
                                                .type("string")
                                                .description("备注，如 中午吃粉")
                                                .build(),
                                        "occur_date", ToolDefinition.Property.builder()
                                                .type("string")
                                                .description("交易日期，格式 YYYY-MM-DD，默认今天")
                                                .build()
                                ))
                                .required(List.of("type", "amount"))
                                .build())
                        .build())
                .build();

        ToolDefinition listCategories = ToolDefinition.builder()
                .type("function")
                .function(ToolDefinition.Function.builder()
                        .name("list_categories")
                        .description("列出可用的交易分类，当用户提到不确定分类时调用此工具")
                        .parameters(ToolDefinition.Parameters.builder()
                                .type("object")
                                .properties(Map.of(
                                        "type", ToolDefinition.Property.builder()
                                                .type("string")
                                                .description("分类类型: income 或 expense")
                                                .enum_(List.of("income", "expense"))
                                                .build()
                                ))
                                .required(List.of("type"))
                                .build())
                        .build())
                .build();

        return List.of(createTransaction, listCategories);
    }

    /** 估算文本的 token 数 */
    private int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        int chineseCount = 0;
        int englishCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseCount++;
            } else if (Character.isLetter(c)) {
                englishCount++;
            }
        }
        return chineseCount * 2 + (int) Math.ceil(englishCount * 1.3);
    }

    /** 标准化 JSON（排序 key，用于生成一致的 hash） */
    private String normalizeJson(String json) {
        if (json == null) {
            return "";
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return objectMapper.writeValueAsString(new TreeMap<>(map));
        } catch (Exception e) {
            return json.trim();
        }
    }

    /** SHA-256 哈希 */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    /** JSON 转 Map */
    private Map<String, Object> parseJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /** JSON 转 ToolCall 列表 */
    private List<ToolCall> parseToolCalls(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 对象转 JSON */
    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 构建错误 JSON */
    private String errorJson(String message) {
        return toJsonString(Map.of("error", nullToEmpty(message)));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 确保工具结果消息已持久化
     *
     * <p>通过 dedupe_key 防止重复插入。
     */
    private void ensureToolResultMessagePersisted(Long sessionId,
                                                  String clientRequestId,
                                                  ToolCall toolCall,
                                                  String arguments,
                                                  String dedupeKey,
                                                  String result,
                                                  String status,
                                                  Long transactionId) {
        ChatMessage existing = chatMessageMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatMessage>query()
                        .eq("dedupe_key", dedupeKey)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }

        ChatMessage toolResultMsg = new ChatMessage();
        toolResultMsg.setSessionId(sessionId);
        toolResultMsg.setRole("tool");
        toolResultMsg.setMsgType("tool_result");
        toolResultMsg.setContent(result);
        toolResultMsg.setClientRequestId(clientRequestId);
        toolResultMsg.setToolCallId(toolCall == null ? null : toolCall.getId());
        toolResultMsg.setToolName(toolCall == null || toolCall.getFunction() == null ? null : toolCall.getFunction().getName());
        toolResultMsg.setToolArguments(arguments);
        toolResultMsg.setToolResult(result);
        toolResultMsg.setToolStatus(status);
        toolResultMsg.setTransactionId(transactionId);
        toolResultMsg.setDedupeKey(dedupeKey);
        insertMessageIgnoreDuplicate(toolResultMsg);
    }

    /** 插入消息，忽略唯一键冲突 */
    private void insertMessageIgnoreDuplicate(ChatMessage message) {
        try {
            chatMessageMapper.insert(message);
        } catch (DuplicateKeyException ignored) {
            // Dedupe key already persisted by a concurrent recovery path.
        }
    }

    /**
     * 在执行任务期间定时发送心跳
     *
     * <p>每 LEASE_HEARTBEAT_SECONDS 刷新一次请求和 tool 执行的租约心跳。
     * 心跳失败时设置 heartbeatFailure，任务结束后抛出异常。
     */
    private <T> T executeWithLeaseHeartbeat(Long sessionId,
                                            Long requestId,
                                            String leaseToken,
                                            ChatToolExecution execution,
                                            Supplier<T> task) {
        AtomicReference<RuntimeException> heartbeatFailure = new AtomicReference<>();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "chat-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            if (heartbeatFailure.get() != null) {
                return;
            }
            try {
                assertRequestOwnership(sessionId, requestId, leaseToken);
                if (execution != null) {
                    touchProcessingExecution(execution);
                }
            } catch (RuntimeException e) {
                heartbeatFailure.compareAndSet(null, e);
            }
        }, LEASE_HEARTBEAT_SECONDS, LEASE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        try {
            T result = task.get();
            RuntimeException heartbeatError = heartbeatFailure.get();
            if (heartbeatError != null) {
                throw heartbeatError;
            }
            return result;
        } finally {
            future.cancel(true);
            executor.shutdownNow();
        }
    }

    /** 檢查请求和会话的租约所有权 */
    private void assertRequestOwnership(Long sessionId, Long requestId, String leaseToken) {
        int requestRows = chatRequestMapper.touchProcessingRequest(requestId, leaseToken);
        int sessionRows = chatSessionMapper.touchSessionExecution(sessionId, requestId, leaseToken);
        if (requestRows <= 0 || sessionRows <= 0) {
            throw new BusinessException(409, "请求已被其他执行流接管");
        }
    }

    /** 刷新工具执行心跳 */
    private void touchProcessingExecution(ChatToolExecution execution) {
        int rows = toolExecutionMapper.touchProcessingExecution(execution.getId(), execution.getLeaseToken());
        if (rows <= 0) {
            throw new BusinessException(409, "工具执行所有权已丢失");
        }
    }

    /** 尝试认领超时的 tool 执行 */
    private boolean tryClaimStaleExecution(ChatToolExecution execution, String newLeaseToken) {
        if (execution.getUpdatedAt() != null
                && execution.getUpdatedAt().isAfter(LocalDateTime.now().minusSeconds(TOOL_EXECUTION_STALE_SECONDS))) {
            return false;
        }
        return toolExecutionMapper.claimStaleExecution(execution.getId(), newLeaseToken) > 0;
    }

    /** 构建 assistant 最终回复去重键 */
    private String buildAssistantMessageDedupeKey(Long sessionId, String clientRequestId) {
        return "assistant-final:" + sessionId + ":" + clientRequestId;
    }

    /** 构建工具结果去重键 */
    private String resultDedupeKey(Long sessionId, String clientRequestId, String toolName, String argsHash) {
        return "tool-result:" + sessionId + ":" + clientRequestId + ":" + nullToEmpty(toolName) + ":" + argsHash;
    }

    private record ToolExecutionResult(String resultJson, Long transactionId, boolean success) {
    }

    private record HistoryBlock(List<LlmMessage> messages, int tokens, boolean fromUser, String clientRequestId) {
    }
}
