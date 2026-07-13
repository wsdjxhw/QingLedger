package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.ChatRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ChatRequestMapper extends BaseMapper<ChatRequest> {

    /** 按 session + 幂等键查询请求 */
    @Select("SELECT * FROM chat_request WHERE session_id = #{sessionId} AND client_request_id = #{clientRequestId} LIMIT 1")
    ChatRequest selectBySessionAndClientRequest(@Param("sessionId") Long sessionId,
                                                @Param("clientRequestId") String clientRequestId);

    /** 认领一个超时的 processing 请求（5分钟无心跳则认领） */
    @Update("""
        UPDATE chat_request
        SET lease_token = #{newLeaseToken},
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'processing'
          AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)
        """)
    int claimProcessingRequest(@Param("id") Long id,
                               @Param("newLeaseToken") String newLeaseToken);

    /** 重置一个 error 状态的请求为 processing，用于重试 */
    @Update("""
        UPDATE chat_request
        SET status = 'processing',
            lease_token = #{newLeaseToken},
            error_message = NULL,
            reply = NULL,
            transaction_id = NULL,
            msg_type = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'error'
        """)
    int retryErroredRequest(@Param("id") Long id,
                            @Param("newLeaseToken") String newLeaseToken);

    /** 恢复被误认领的请求（处理冲突后回退） */
    @Update("""
        UPDATE chat_request
        SET lease_token = #{previousLeaseToken},
            updated_at = #{previousUpdatedAt}
        WHERE id = #{id}
          AND status = 'processing'
          AND lease_token = #{currentLeaseToken}
        """)
    int restoreClaimedProcessingRequest(@Param("id") Long id,
                                        @Param("currentLeaseToken") String currentLeaseToken,
                                        @Param("previousLeaseToken") String previousLeaseToken,
                                        @Param("previousUpdatedAt") LocalDateTime previousUpdatedAt);

    /** 刷新请求心跳 */
    @Update("""
        UPDATE chat_request
        SET updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'processing'
          AND lease_token = #{leaseToken}
        """)
    int touchProcessingRequest(@Param("id") Long id,
                               @Param("leaseToken") String leaseToken);

    /** 标记请求为成功 */
    @Update("""
        UPDATE chat_request
        SET status = 'success',
            reply = #{reply},
            transaction_id = #{transactionId},
            msg_type = #{msgType},
            error_message = NULL
        WHERE id = #{id}
          AND status = 'processing'
          AND lease_token = #{leaseToken}
        """)
    int markSuccess(@Param("id") Long id,
                    @Param("leaseToken") String leaseToken,
                    @Param("reply") String reply,
                    @Param("transactionId") Long transactionId,
                    @Param("msgType") String msgType);

    /** 标记请求为失败 */
    @Update("""
        UPDATE chat_request
        SET status = 'error',
            error_message = #{errorMessage}
        WHERE id = #{id}
          AND status = 'processing'
          AND lease_token = #{leaseToken}
        """)
    int markError(@Param("id") Long id,
                  @Param("leaseToken") String leaseToken,
                  @Param("errorMessage") String errorMessage);
}
