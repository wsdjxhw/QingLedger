package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /** 尝试获取会话级分布式锁（支持无锁、重入、超时抢锁三种场景） */
    @Update("""
        UPDATE chat_session
        SET current_request_id = #{requestId},
            current_request_lease_token = #{leaseToken},
            current_request_heartbeat_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{sessionId}
          AND (
                current_request_id IS NULL
                OR (current_request_id = #{requestId}
                    AND current_request_lease_token = #{leaseToken})
                OR current_request_heartbeat_at IS NULL
                OR current_request_heartbeat_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)
              )
        """)
    int claimSessionExecution(@Param("sessionId") Long sessionId,
                              @Param("requestId") Long requestId,
                              @Param("leaseToken") String leaseToken);

    /** 刷新会话级分布式锁的心跳时间 */
    @Update("""
        UPDATE chat_session
        SET current_request_heartbeat_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{sessionId}
          AND current_request_id = #{requestId}
          AND current_request_lease_token = #{leaseToken}
        """)
    int touchSessionExecution(@Param("sessionId") Long sessionId,
                              @Param("requestId") Long requestId,
                              @Param("leaseToken") String leaseToken);

    /** 释放会话级分布式锁 */
    @Update("""
        UPDATE chat_session
        SET current_request_id = NULL,
            current_request_lease_token = NULL,
            current_request_heartbeat_at = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{sessionId}
          AND current_request_id = #{requestId}
          AND current_request_lease_token = #{leaseToken}
        """)
    int releaseSessionExecution(@Param("sessionId") Long sessionId,
                                @Param("requestId") Long requestId,
                                @Param("leaseToken") String leaseToken);
}
