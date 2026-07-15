package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 关联账本 ID */
    private Long ledgerId;

    /** 会话标题 */
    private String title;

    /** 当前人设编码 */
    private String personaType;

    /** 会话状态：active / archived */
    private String status;

    /** 当前正在执行的请求 ID（分布式锁） */
    private Long currentRequestId;

    /** 当前请求的租约令牌（分布式锁） */
    private String currentRequestLeaseToken;

    /** 当前请求的心跳时间（用于检测过期锁） */
    private LocalDateTime currentRequestHeartbeatAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
