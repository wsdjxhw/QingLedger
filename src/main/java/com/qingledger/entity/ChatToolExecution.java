package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_tool_execution")
public class ChatToolExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的 chat_request ID */
    private Long requestId;

    /** 工具名称，如 create_transaction */
    private String toolName;

    /** 参数 SHA-256 哈希（用于幂等判断） */
    private String argumentsHash;

    /** 幂等键：requestId + toolName + argumentsHash */
    private String idempotentKey;

    /** 处理租约令牌（分布式锁） */
    private String leaseToken;

    /** 关联的交易 ID */
    private Long transactionId;

    /** 工具执行结果 JSON */
    private String resultJson;

    /** 状态：processing / success / error */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
