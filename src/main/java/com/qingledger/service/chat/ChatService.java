package com.qingledger.service.chat;

import com.qingledger.entity.ChatMessage;
import com.qingledger.entity.ChatSession;
import com.qingledger.entity.PersonaTemplate;
import com.qingledger.vo.ChatResponse;

import java.util.List;

public interface ChatService {

    /** 创建 AI 对话会话 */
    Long createSession(Long userId, Long ledgerId, String personaType);

    /** 获取用户的会话列表 */
    List<ChatSession> listSessions(Long userId);

    /** 发送消息并执行 AI 记账 */
    ChatResponse sendMessage(Long userId, Long sessionId, String content, String clientRequestId);

    /** 获取会话的历史消息 */
    List<ChatMessage> getMessages(Long userId, Long sessionId);

    /** 切换会话的人设 */
    void switchPersona(Long userId, Long sessionId, String personaType);

    /** 获取可用人设列表 */
    List<PersonaTemplate> listPersonas();
}
