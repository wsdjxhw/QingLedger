package com.qingledger.controller;

import com.qingledger.common.BusinessException;
import com.qingledger.common.Result;
import com.qingledger.dto.request.CreateSessionRequest;
import com.qingledger.dto.request.SendMessageRequest;
import com.qingledger.entity.ChatMessage;
import com.qingledger.entity.ChatSession;
import com.qingledger.entity.PersonaTemplate;
import com.qingledger.service.chat.ChatService;
import com.qingledger.utils.UserContext;
import com.qingledger.vo.ChatMessageResponse;
import com.qingledger.vo.ChatResponse;
import com.qingledger.vo.ChatSessionResponse;
import com.qingledger.vo.PersonaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI 记账助手", description = "对话式 AI 记账接口")
@Validated
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 创建 AI 对话会话 */
    @Operation(summary = "创建会话", description = "创建 AI 记账对话会话，指定账本与人设，可选标题", security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/session")
    public Result<Long> createSession(@Valid @RequestBody CreateSessionRequest req) {
        Long userId = getCurrentUserId();
        Long sessionId = chatService.createSession(userId, req.getLedgerId(), req.getPersonaType(), req.getTitle());
        return Result.ok(sessionId);
    }

    /** 获取当前用户的会话列表 */
    @Operation(summary = "会话列表", description = "获取当前用户的有效会话列表", security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/sessions")
    public Result<List<ChatSessionResponse>> listSessions() {
        Long userId = getCurrentUserId();
        return Result.ok(chatService.listSessions(userId).stream().map(this::toSessionResponse).toList());
    }

    /** 向 AI 发送消息并执行记账 */
    @Operation(summary = "发送消息", description = "向 AI 发送消息并执行记账", security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/session/{sessionId}/message")
    public Result<ChatResponse> sendMessage(@PathVariable Long sessionId,
                                            @Valid @RequestBody SendMessageRequest req) {
        Long userId = getCurrentUserId();
        return Result.ok(chatService.sendMessage(userId, sessionId, req.getContent(), req.getClientRequestId()));
    }

    /** 获取指定会话的历史消息 */
    @Operation(summary = "历史消息", description = "获取指定会话的历史消息", security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/session/{sessionId}/messages")
    public Result<List<ChatMessageResponse>> getMessages(@PathVariable Long sessionId) {
        Long userId = getCurrentUserId();
        return Result.ok(chatService.getMessages(userId, sessionId).stream().map(this::toMessageResponse).toList());
    }

    /** 获取可用人设列表 */
    @Operation(summary = "人设列表", description = "获取可用人设模板", security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/personas")
    public Result<List<PersonaResponse>> listPersonas() {
        Long userId = getCurrentUserId();
        return Result.ok(chatService.listPersonas().stream().map(this::toPersonaResponse).toList());
    }

    /** 切换会话的人设 */
    @Operation(summary = "切换人设", description = "切换指定会话的人设", security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/session/{sessionId}/persona")
    public Result<Void> switchPersona(@PathVariable Long sessionId,
                                      @RequestParam
                                      @NotBlank(message = "personaType 不能为空")
                                      @Size(max = 20, message = "personaType 长度不能超过20")
                                      String personaType) {
        if (personaType.length() > 20) {
            throw new BusinessException(400, "personaType 长度不能超过20");
        }
        Long userId = getCurrentUserId();
        chatService.switchPersona(userId, sessionId, personaType);
        return Result.ok();
    }

    /** 重命名会话 */
    @Operation(summary = "重命名会话", description = "修改指定会话的标题", security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/session/{sessionId}/title")
    public Result<Void> renameSession(@PathVariable Long sessionId,
                                      @RequestParam
                                      @NotBlank(message = "title 不能为空")
                                      @Size(max = 100, message = "标题长度不能超过100")
                                      String title) {
        if (title.trim().length() > 100) {
            throw new BusinessException(400, "标题长度不能超过100");
        }
        Long userId = getCurrentUserId();
        chatService.renameSession(userId, sessionId, title);
        return Result.ok();
    }

    /** 删除会话（软删除，数据保留） */
    @Operation(summary = "删除会话", description = "软删除指定会话，消息数据保留", security = @SecurityRequirement(name = "JWT"))
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = getCurrentUserId();
        chatService.deleteSession(userId, sessionId);
        return Result.ok();
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        ChatSessionResponse response = new ChatSessionResponse();
        response.setId(session.getId());
        response.setLedgerId(session.getLedgerId());
        response.setTitle(session.getTitle());
        response.setPersonaType(session.getPersonaType());
        response.setStatus(session.getStatus());
        response.setCreatedAt(session.getCreatedAt());
        response.setUpdatedAt(session.getUpdatedAt());
        return response;
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setClientRequestId(message.getClientRequestId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setMsgType(message.getMsgType());
        response.setTransactionId(message.getTransactionId());
        response.setToolCallId(message.getToolCallId());
        response.setToolName(message.getToolName());
        response.setToolCallsJson(message.getToolCallsJson());
        response.setToolResult(message.getToolResult());
        response.setToolStatus(message.getToolStatus());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }

    private PersonaResponse toPersonaResponse(PersonaTemplate template) {
        PersonaResponse response = new PersonaResponse();
        response.setId(template.getId());
        response.setCode(template.getCode());
        response.setName(template.getName());
        response.setDescription(template.getDescription());
        response.setAvatar(template.getAvatar());
        response.setSortOrder(template.getSortOrder());
        return response;
    }
}
