package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_request")
public class ChatRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID */
    private Long sessionId;

    /** 发起请求的用户 ID */
    private Long userId;

    /** 客户端请求幂等 ID（session 内唯一） */
    private String clientRequestId;

    /** 处理租约令牌（分布式锁） */
    private String leaseToken;

    /** 当前人设编码 */
    private String personaType;

    /** 请求状态：processing / success / error */
    private String status;

    /** 用户原始消息内容 */
    private String userContent;

    /** 最终助手回复 */
    private String reply;

    /** 关联的交易 ID */
    private Long transactionId;

    /** 最终消息类型：text / transaction */
    private String msgType;

    /** 错误信息 */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
