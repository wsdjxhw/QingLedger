package com.qingledger.scheduled;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qingledger.entity.InvitationCode;
import com.qingledger.mapper.InvitationCodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 邀请码自动清理定时任务
 *
 * 功能：
 * - 定期清理90天前失效的邀请码
 * - 避免数据库积累过多历史数据
 * - 保持查询性能
 *
 * 清理规则：
 * - 只清理状态为'revoked'的邀请码
 * - 只清理创建时间超过90天的记录
 * - 保留近期失效的记录用于审计
 *
 * @author QingLedger Team
 */
@Slf4j
@Component
public class InvitationCodeCleanupTask {

    private final InvitationCodeMapper invitationCodeMapper;

    /**
     * 清理天数阈值：90天
     */
    private static final int CLEANUP_DAYS_THRESHOLD = 90;

    public InvitationCodeCleanupTask(InvitationCodeMapper invitationCodeMapper) {
        this.invitationCodeMapper = invitationCodeMapper;
    }

    /**
     * 自动清理过期邀请码
     *
     * 执行时间：每天凌晨2点执行
     * 清理范围：所有账本的90天前失效邀请码
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredInvitations() {
        log.info("开始执行邀请码自动清理任务");

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(CLEANUP_DAYS_THRESHOLD);

            int deletedCount = invitationCodeMapper.delete(
                Wrappers.<InvitationCode>query()
                    .eq("status", "revoked")
                    .lt("created_at", cutoffDate)
            );

            log.info("邀请码自动清理完成，删除数量: {}", deletedCount);

            if (deletedCount > 0) {
                log.info("清理了 {} 个超过 {} 天的失效邀请码", deletedCount, CLEANUP_DAYS_THRESHOLD);
            }

        } catch (Exception e) {
            log.error("邀请码自动清理任务执行失败", e);
        }
    }

    /**
     * 手动触发清理（用于测试或紧急清理）
     *
     * 可以通过调用此方法手动触发清理，而不等待定时执行
     */
    public void manualCleanup() {
        log.info("手动触发邀请码清理");
        cleanupExpiredInvitations();
    }
}
