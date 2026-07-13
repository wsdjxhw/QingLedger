package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.ChatToolExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatToolExecutionMapper extends BaseMapper<ChatToolExecution> {

    /** 按幂等键查询工具执行记录 */
    @Select("SELECT * FROM chat_tool_execution WHERE idempotent_key = #{idempotentKey} LIMIT 1")
    ChatToolExecution selectByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    /** 重试失败的 tool 执行（重置为 processing） */
    @Update("""
        UPDATE chat_tool_execution
        SET status = 'processing',
            lease_token = #{newLeaseToken},
            result_json = NULL,
            transaction_id = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'error'
        """)
    int retryErroredExecution(@Param("id") Long id,
                              @Param("newLeaseToken") String newLeaseToken);

    /** 刷新 tool 执行心跳 */
    @Update("""
        UPDATE chat_tool_execution
        SET updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'processing'
          AND lease_token = #{leaseToken}
        """)
    int touchProcessingExecution(@Param("id") Long id,
                                 @Param("leaseToken") String leaseToken);

    /** 认领超时的 tool 执行（5分钟无心跳则认领） */
    @Update("""
        UPDATE chat_tool_execution
        SET lease_token = #{newLeaseToken},
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'processing'
          AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)
        """)
    int claimStaleExecution(@Param("id") Long id,
                            @Param("newLeaseToken") String newLeaseToken);

    /** 标记 tool 执行为成功 */
    @Update("""
        UPDATE chat_tool_execution
        SET status = 'success',
            result_json = #{resultJson},
            transaction_id = #{transactionId},
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'processing'
          AND lease_token = #{leaseToken}
        """)
    int markSuccess(@Param("id") Long id,
                    @Param("leaseToken") String leaseToken,
                    @Param("resultJson") String resultJson,
                    @Param("transactionId") Long transactionId);

    /** 标记 tool 执行为失败 */
    @Update("""
        UPDATE chat_tool_execution
        SET status = 'error',
            result_json = #{resultJson},
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'processing'
          AND lease_token = #{leaseToken}
        """)
    int markError(@Param("id") Long id,
                  @Param("leaseToken") String leaseToken,
                  @Param("resultJson") String resultJson);
}
