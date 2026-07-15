package com.qingledger.service.chat.impl;

import com.qingledger.common.BusinessException;
import com.qingledger.entity.ChatMessage;
import com.qingledger.entity.ChatRequest;
import com.qingledger.entity.ChatSession;
import com.qingledger.entity.ChatToolExecution;
import com.qingledger.entity.Ledger;
import com.qingledger.entity.LedgerMember;
import com.qingledger.entity.PersonaTemplate;
import com.qingledger.mapper.ChatMessageMapper;
import com.qingledger.mapper.ChatRequestMapper;
import com.qingledger.mapper.ChatSessionMapper;
import com.qingledger.mapper.ChatToolExecutionMapper;
import com.qingledger.mapper.LedgerMapper;
import com.qingledger.mapper.LedgerMemberMapper;
import com.qingledger.mapper.PersonaTemplateMapper;
import com.qingledger.service.chat.ChatService;
import com.qingledger.service.chat.agent.BookingAgent;
import com.qingledger.vo.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final long REQUEST_STALE_SECONDS = 300;
    private static final String DEFAULT_PERSONA = "cute_pet";

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatRequestMapper chatRequestMapper;
    private final ChatToolExecutionMapper chatToolExecutionMapper;
    private final PersonaTemplateMapper personaTemplateMapper;
    private final LedgerMemberMapper ledgerMemberMapper;
    private final LedgerMapper ledgerMapper;
    private final BookingAgent bookingAgent;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper,
                           ChatMessageMapper chatMessageMapper,
                           ChatRequestMapper chatRequestMapper,
                           ChatToolExecutionMapper chatToolExecutionMapper,
                           PersonaTemplateMapper personaTemplateMapper,
                           LedgerMemberMapper ledgerMemberMapper,
                           LedgerMapper ledgerMapper,
                           BookingAgent bookingAgent) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatRequestMapper = chatRequestMapper;
        this.chatToolExecutionMapper = chatToolExecutionMapper;
        this.personaTemplateMapper = personaTemplateMapper;
        this.ledgerMemberMapper = ledgerMemberMapper;
        this.ledgerMapper = ledgerMapper;
        this.bookingAgent = bookingAgent;
    }

    /** 创建 AI 对话会话 */
    @Override
    @Transactional
    public Long createSession(Long userId, Long ledgerId, String personaType, String title) {
        validateLedgerAccess(userId, ledgerId);
        validatePersonaType(personaType);

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setLedgerId(ledgerId);
        session.setTitle(title != null && !title.isBlank() ? title.trim() : null);
        session.setPersonaType(personaType);
        session.setStatus("active");
        chatSessionMapper.insert(session);
        return session.getId();
    }

    /** 获取用户的会话列表（仅返回有权限且活跃的会话） */
    @Override
    public List<ChatSession> listSessions(Long userId) {
        return chatSessionMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatSession>query()
                        .eq("user_id", userId)
                        .eq("status", "active")
                        .orderByDesc("updated_at")
        ).stream()
                .filter(session -> hasLedgerAccess(userId, session.getLedgerId()) && isLedgerActive(session.getLedgerId()))
                .toList();
    }

    /**
     * 发送消息并执行 AI 记账
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>查找或创建请求记录（幂等键保证唯一）</li>
     *   <li>根据请求状态分岔：成功返回缓存结果，失败重试，超时抢锁</li>
     *   <li>执行前尝试恢复已完成的请求或部分完成的工具副作用</li>
     *   <li>提交给 BookingAgent 执行 AI 对话 + 工具调用</li>
     *   <li>异常恢复兜底：再次尝试恢复已完成/部分完成的结果</li>
     * </ol>
     */
    @Override
    public ChatResponse sendMessage(Long userId, Long sessionId, String content, String clientRequestId) {
        ChatSession session = validateSessionAccess(userId, sessionId);
        validateLedgerAccess(userId, session.getLedgerId());
        RequestClaim claim = findOrCreateChatRequest(sessionId, userId, clientRequestId, content);
        ChatRequest request = claim.request();

        if ("success".equals(request.getStatus())) {
            ensureUserMessagePersisted(sessionId, clientRequestId, request.getUserContent());
            persistRecoveredAssistantMessage(sessionId, clientRequestId, toResponse(request));
            touchSession(sessionId);
            return toResponse(request);
        }

        boolean sessionClaimed = false;
        try {
            if ("error".equals(request.getStatus())) {
                sessionClaimed = claimSessionExecution(sessionId, request.getId(), claim.leaseToken());
                if (!sessionClaimed) {
                    throw new BusinessException(409, "当前会话仍有消息处理中，请稍后再试");
                }
                if (!retryErroredRequest(request, claim.leaseToken())) {
                    throw new BusinessException(500, "请求处理失败: " + nullToEmpty(request.getErrorMessage()));
                }
                resetRequestLease(request, claim.leaseToken());
            } else if (claim.claimed()) {
                sessionClaimed = claimSessionExecution(sessionId, request.getId(), claim.leaseToken());
                if (!sessionClaimed) {
                    chatRequestMapper.deleteById(request.getId());
                    throw new BusinessException(409, "当前会话仍有消息处理中，请稍后再试");
                }
            } else {
                String previousLeaseToken = request.getLeaseToken();
                LocalDateTime previousUpdatedAt = request.getUpdatedAt();
                if (!tryClaimStaleProcessing(request, claim.leaseToken())) {
                    throw new BusinessException(409, "请求处理中，请勿重复提交");
                }
                request.setLeaseToken(claim.leaseToken());
                sessionClaimed = claimSessionExecution(sessionId, request.getId(), claim.leaseToken());
                if (!sessionClaimed) {
                    restoreClaimedProcessingRequest(request.getId(), claim.leaseToken(), previousLeaseToken, previousUpdatedAt);
                    throw new BusinessException(409, "当前会话仍有消息处理中，请稍后再试");
                }
                request.setStatus("processing");
            }

            ChatResponse recovered = tryRecoverCompletedRequest(request);
            if (recovered != null) {
                touchSession(sessionId);
                return recovered;
            }

            ChatResponse partial = tryRecoverToolSideEffect(request);
            if (partial != null) {
                ensureUserMessagePersisted(sessionId, clientRequestId, content);
                persistRecoveredAssistantMessage(sessionId, clientRequestId, partial);
                markRequestSuccess(request, partial);
                touchSession(sessionId);
                return partial;
            }

            touchProcessingRequest(sessionId, request);
            String personaPrompt = loadActivePersonaPrompt(request.getPersonaType());
            List<ChatMessage> history = chatMessageMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatMessage>query()
                            .eq("session_id", sessionId)
                            .and(wrapper -> wrapper.isNull("client_request_id").or().ne("client_request_id", clientRequestId))
                            .orderByAsc("id")
            );

            ensureUserMessagePersisted(sessionId, clientRequestId, content);

            ChatResponse response = bookingAgent.run(
                    request.getId(),
                    request.getLeaseToken(),
                    sessionId,
                    clientRequestId,
                    content,
                    history,
                    personaPrompt,
                    session.getLedgerId(),
                    userId
            );

            markRequestSuccess(request, response);
            autoNameSession(session, content);
            touchSession(sessionId);
            return response;
        } catch (Exception e) {
            log.error("sendMessage failed: sessionId={}, clientRequestId={}", sessionId, clientRequestId, e);

            ChatResponse recovered = tryRecoverCompletedRequest(request);
            if (recovered != null) {
                touchSession(sessionId);
                return recovered;
            }

            ChatResponse partial = tryRecoverToolSideEffect(request);
            if (partial != null) {
                ensureUserMessagePersisted(sessionId, clientRequestId, content);
                persistRecoveredAssistantMessage(sessionId, clientRequestId, partial);
                markRequestSuccess(request, partial);
                touchSession(sessionId);
                return partial;
            }

            markRequestError(request, e.getMessage());

            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(500, "消息处理失败");
        } finally {
            if (sessionClaimed) {
                releaseSessionExecution(sessionId, request.getId(), request.getLeaseToken());
            }
        }
    }

    /** 获取会话的历史消息 */
    @Override
    public List<ChatMessage> getMessages(Long userId, Long sessionId) {
        ChatSession session = validateSessionAccess(userId, sessionId);
        validateLedgerAccess(userId, session.getLedgerId());
        return chatMessageMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatMessage>query()
                        .eq("session_id", sessionId)
                        .orderByAsc("id")
        );
    }

    /** 切换会话的人设 */
    @Override
    public void switchPersona(Long userId, Long sessionId, String personaType) {
        ChatSession session = validateSessionAccess(userId, sessionId);
        validateLedgerAccess(userId, session.getLedgerId());
        validatePersonaType(personaType);
        chatSessionMapper.update(
                null,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatSession>update()
                        .eq("id", sessionId)
                        .eq("user_id", userId)
                        .set("persona_type", personaType)
        );
    }

    /** 重命名会话 */
    @Override
    public void renameSession(Long userId, Long sessionId, String title) {
        ChatSession session = validateSessionAccess(userId, sessionId);
        validateLedgerAccess(userId, session.getLedgerId());
        chatSessionMapper.update(
                null,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatSession>update()
                        .eq("id", sessionId)
                        .eq("user_id", userId)
                        .set("title", title != null ? title.trim() : null)
        );
    }

    /** 删除会话（软删除） */
    @Override
    public void deleteSession(Long userId, Long sessionId) {
        ChatSession session = validateSessionAccess(userId, sessionId);
        validateLedgerAccess(userId, session.getLedgerId());
        chatSessionMapper.update(
                null,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatSession>update()
                        .eq("id", sessionId)
                        .eq("user_id", userId)
                        .set("status", "deleted")
        );
    }

    /** 如果会话没有标题则自动从首条消息生成 */
    private void autoNameSession(ChatSession session, String content) {
        if (session.getTitle() != null && !session.getTitle().isBlank()) {
            return;
        }
        String title = content.length() > 25 ? content.substring(0, 25) + "..." : content;
        chatSessionMapper.update(
                null,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatSession>update()
                        .eq("id", session.getId())
                        .set("title", title)
        );
    }

    /** 获取启用人设列表 */
    @Override
    public List<PersonaTemplate> listPersonas() {
        return personaTemplateMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<PersonaTemplate>query()
                        .eq("is_active", 1)
                        .orderByAsc("sort_order")
        );
    }

    /** 校验用户对会话的访问权限 */
    private ChatSession validateSessionAccess(Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(404, "会话不存在");
        }
        return session;
    }

    /** 校验用户对账本的访问权限 */
    private void validateLedgerAccess(Long userId, Long ledgerId) {
        if (!hasLedgerAccess(userId, ledgerId)) {
            throw new BusinessException(403, "无权访问该账本");
        }
        if (!isLedgerActive(ledgerId)) {
            throw new BusinessException(400, "账本已归档，无法执行该操作");
        }
    }

    /**
     * 查找或创建请求记录
     *
     * <p>通过 client_request_id + session_id 的唯一约束保证幂等。
     * 不存在则新建并返回 {@code claimed=true}；
     * 已存在则返回现有记录 {@code claimed=false}（调用方需处理抢锁逻辑）。
     */
    protected RequestClaim findOrCreateChatRequest(Long sessionId, Long userId, String clientRequestId, String content) {
        ChatRequest existing = chatRequestMapper.selectBySessionAndClientRequest(sessionId, clientRequestId);
        if (existing != null) {
            if (!Objects.equals(existing.getUserContent(), content)) {
                throw new BusinessException(400, "同一 clientRequestId 不能复用于不同消息");
            }
            return new RequestClaim(existing, false, UUID.randomUUID().toString());
        }

        ChatRequest request = new ChatRequest();
        ChatSession session = chatSessionMapper.selectById(sessionId);
        String leaseToken = UUID.randomUUID().toString();
        request.setSessionId(sessionId);
        request.setUserId(userId);
        request.setClientRequestId(clientRequestId);
        request.setLeaseToken(leaseToken);
        request.setPersonaType(session == null || session.getPersonaType() == null ? DEFAULT_PERSONA : session.getPersonaType());
        request.setStatus("processing");
        request.setUserContent(content);
        try {
            chatRequestMapper.insert(request);
            return new RequestClaim(request, true, leaseToken);
        } catch (Exception e) {
            ChatRequest inserted = chatRequestMapper.selectBySessionAndClientRequest(sessionId, clientRequestId);
            if (inserted != null) {
                if (!Objects.equals(inserted.getUserContent(), content)) {
                    throw new BusinessException(400, "同一 clientRequestId 不能复用于不同消息");
                }
                return new RequestClaim(inserted, false, UUID.randomUUID().toString());
            }
            throw new BusinessException(500, "请求处理失败");
        }
    }

    /**
     * 尝试恢复已完成的请求
     *
     * <p>如果 assistant 回复消息已持久化（通过 dedupe_key 或 client_request_id 查找），
     * 则直接返回缓存结果，避免重复执行 LLM 调用。
     */
    private ChatResponse tryRecoverCompletedRequest(ChatRequest request) {
        ChatMessage assistantMsg = chatMessageMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatMessage>query()
                        .eq("dedupe_key", buildAssistantMessageDedupeKey(request.getSessionId(), request.getClientRequestId()))
                        .last("LIMIT 1")
        );
        if (assistantMsg == null) {
            assistantMsg = chatMessageMapper.selectOne(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatMessage>query()
                            .eq("session_id", request.getSessionId())
                            .eq("client_request_id", request.getClientRequestId())
                            .eq("role", "assistant")
                            .in("msg_type", "text", "transaction")
                            .orderByDesc("id")
                            .last("LIMIT 1")
            );
        }
        if (assistantMsg == null) {
            return null;
        }

        ChatResponse recovered = new ChatResponse(
                assistantMsg.getContent(),
                assistantMsg.getTransactionId(),
                assistantMsg.getMsgType()
        );
        markRequestSuccess(request, recovered);
        return recovered;
    }

    /**
     * 尝试恢复部分完成的工具副作用
     *
     * <p>如果请求失败但工具执行已成功创建交易（transaction_id 非空），
     * 则返回已创建的交易信息，避免丢失记账结果。
     */
    private ChatResponse tryRecoverToolSideEffect(ChatRequest request) {
        ChatToolExecution execution = chatToolExecutionMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatToolExecution>query()
                        .eq("request_id", request.getId())
                        .eq("status", "success")
                        .isNotNull("transaction_id")
                        .orderByDesc("id")
                        .last("LIMIT 1")
        );
        if (execution == null || execution.getTransactionId() == null) {
            return null;
        }

        return new ChatResponse(
                "已完成记账，本次回复在生成时中断，请刷新会话查看详情。",
                execution.getTransactionId(),
                "transaction"
        );
    }

    /** 尝试认领超时的 processing 请求（5分钟无心跳则认领） */
    private boolean tryClaimStaleProcessing(ChatRequest request, String newLeaseToken) {
        LocalDateTime updatedAt = request.getUpdatedAt();
        if (updatedAt != null && updatedAt.isAfter(LocalDateTime.now().minusSeconds(REQUEST_STALE_SECONDS))) {
            return false;
        }
        return chatRequestMapper.claimProcessingRequest(request.getId(), newLeaseToken) > 0;
    }

    /** 重试失败的请求（重置为 processing） */
    private boolean retryErroredRequest(ChatRequest request, String newLeaseToken) {
        return chatRequestMapper.retryErroredRequest(request.getId(), newLeaseToken) > 0;
    }

    /** 恢复被误认领的请求租约（处理会话锁冲突后回退） */
    private void restoreClaimedProcessingRequest(Long requestId,
                                                 String currentLeaseToken,
                                                 String previousLeaseToken,
                                                 LocalDateTime previousUpdatedAt) {
        chatRequestMapper.restoreClaimedProcessingRequest(
                requestId,
                currentLeaseToken,
                previousLeaseToken,
                previousUpdatedAt == null ? LocalDateTime.now().minusSeconds(REQUEST_STALE_SECONDS + 1) : previousUpdatedAt
        );
    }

    /** 刷新请求 + 会话的心跳 */
    private void touchProcessingRequest(Long sessionId, ChatRequest request) {
        int requestRows = chatRequestMapper.touchProcessingRequest(request.getId(), request.getLeaseToken());
        int sessionRows = chatSessionMapper.touchSessionExecution(sessionId, request.getId(), request.getLeaseToken());
        if (requestRows <= 0 || sessionRows <= 0) {
            throw new BusinessException(409, "请求已被其他执行流接管");
        }
        request.setUpdatedAt(LocalDateTime.now());
    }

    /** 确保用户消息已持久化（幂等） */
    private void ensureUserMessagePersisted(Long sessionId, String clientRequestId, String content) {
        ChatMessage existing = chatMessageMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatMessage>query()
                        .eq("dedupe_key", buildUserMessageDedupeKey(sessionId, clientRequestId))
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(content);
        userMsg.setMsgType("text");
        userMsg.setClientRequestId(clientRequestId);
        userMsg.setDedupeKey(buildUserMessageDedupeKey(sessionId, clientRequestId));
        insertMessageIgnoreDuplicate(userMsg);
    }

    /** 确保恢复的 assistant 消息已持久化（幂等） */
    private void persistRecoveredAssistantMessage(Long sessionId, String clientRequestId, ChatResponse response) {
        ChatMessage existing = chatMessageMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatMessage>query()
                        .eq("dedupe_key", buildAssistantMessageDedupeKey(sessionId, clientRequestId))
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }

        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(response.getReply());
        assistantMsg.setMsgType(response.getMsgType());
        assistantMsg.setTransactionId(response.getTransactionId());
        assistantMsg.setClientRequestId(clientRequestId);
        assistantMsg.setDedupeKey(buildAssistantMessageDedupeKey(sessionId, clientRequestId));
        insertMessageIgnoreDuplicate(assistantMsg);
    }

    /** 标记请求为成功（带 lease 校验） */
    private void markRequestSuccess(ChatRequest request, ChatResponse response) {
        request.setStatus("success");
        request.setReply(response.getReply());
        request.setTransactionId(response.getTransactionId());
        request.setMsgType(response.getMsgType());
        request.setErrorMessage(null);
        int rows = chatRequestMapper.markSuccess(
                request.getId(),
                request.getLeaseToken(),
                response.getReply(),
                response.getTransactionId(),
                response.getMsgType()
        );
        if (rows <= 0) {
            throw new BusinessException(409, "请求已被其他执行流接管");
        }
    }

    /** 标记请求为失败（lease 丢失时不抛异常，仅记录日志） */
    private void markRequestError(ChatRequest request, String errorMessage) {
        request.setStatus("error");
        String safeErrorMessage = nullToEmpty(errorMessage);
        request.setErrorMessage(safeErrorMessage);
        int rows = chatRequestMapper.markError(request.getId(), request.getLeaseToken(), safeErrorMessage);
        if (rows <= 0) {
            log.warn("markRequestError skipped because lease lost: requestId={}", request.getId());
        }
    }

    /** 刷新会话的 updated_at */
    private void touchSession(Long sessionId) {
        chatSessionMapper.update(
                null,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatSession>update()
                        .eq("id", sessionId)
                        .set("updated_at", LocalDateTime.now())
        );
    }

    /** 尝试获取会话级分布式锁 */
    private boolean claimSessionExecution(Long sessionId, Long requestId, String leaseToken) {
        return chatSessionMapper.claimSessionExecution(sessionId, requestId, leaseToken) > 0;
    }

    /** 释放会话级分布式锁 */
    private void releaseSessionExecution(Long sessionId, Long requestId, String leaseToken) {
        chatSessionMapper.releaseSessionExecution(sessionId, requestId, leaseToken);
    }

    /** 加载启用的人设 system prompt */
    private String loadActivePersonaPrompt(String personaType) {
        String finalPersonaType = personaType == null ? DEFAULT_PERSONA : personaType;
        PersonaTemplate template = personaTemplateMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<PersonaTemplate>query()
                        .eq("code", finalPersonaType)
                        .eq("is_active", 1)
        );
        if (template == null) {
            throw new BusinessException(400, "当前会话人设已失效，请重新选择");
        }
        return template.getSystemPrompt();
    }

    /** 校验人设编码是否有效且已启用 */
    private void validatePersonaType(String personaType) {
        if (personaType == null || personaType.isBlank()) {
            throw new BusinessException(400, "personaType 不能为空");
        }
        Long count = personaTemplateMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<PersonaTemplate>query()
                        .eq("code", personaType)
                        .eq("is_active", 1)
        );
        if (count == null || count == 0) {
            throw new BusinessException(400, "人设不存在或未启用");
        }
    }

    /** 重置请求为 processing 状态 */
    private void resetRequestLease(ChatRequest request, String leaseToken) {
        request.setStatus("processing");
        request.setErrorMessage(null);
        request.setReply(null);
        request.setTransactionId(null);
        request.setMsgType(null);
        request.setLeaseToken(leaseToken);
    }

    /** ChatRequest 转 ChatResponse */
    private ChatResponse toResponse(ChatRequest request) {
        return new ChatResponse(request.getReply(), request.getTransactionId(), request.getMsgType());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 构建用户消息去重键 */
    private String buildUserMessageDedupeKey(Long sessionId, String clientRequestId) {
        return "user:" + sessionId + ":" + clientRequestId;
    }

    /** 构建 assistant 最终回复去重键 */
    private String buildAssistantMessageDedupeKey(Long sessionId, String clientRequestId) {
        return "assistant-final:" + sessionId + ":" + clientRequestId;
    }

    /** 插入消息，忽略唯一键冲突 */
    private void insertMessageIgnoreDuplicate(ChatMessage message) {
        try {
            chatMessageMapper.insert(message);
        } catch (DuplicateKeyException ignored) {
            // Dedupe key already persisted by a concurrent recovery path.
        }
    }

    /** 检查用户是否在账本成员中 */
    private boolean hasLedgerAccess(Long userId, Long ledgerId) {
        Long count = ledgerMemberMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<LedgerMember>query()
                        .eq("ledger_id", ledgerId)
                        .eq("user_id", userId)
        );
        return count != null && count > 0;
    }

    /** 检查账本是否未归档 */
    private boolean isLedgerActive(Long ledgerId) {
        Ledger ledger = ledgerMapper.selectById(ledgerId);
        return ledger != null && (ledger.getStatus() == null || ledger.getStatus() != 0);
    }

    private record RequestClaim(ChatRequest request, boolean claimed, String leaseToken) {
    }
}
