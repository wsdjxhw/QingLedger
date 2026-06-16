package com.qingledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * QingLedger 智能记账系统主应用
 *
 * 核心功能:
 * - 传统记账(多账本、分类、标签)
 * - 对话式记账(支持AI交互)
 * - AI智能反馈(可切换人设)
 * - 数据统计分析
 *
 * 技术栈: Spring Boot 3.2 + MyBatis-Plus + Redis + RabbitMQ + Elasticsearch
 *
 * @author QingLedger Team
 * @since 2026-03-28
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class QingLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QingLedgerApplication.class, args);
        System.out.println("""

            ========================================
               QingLedger 智能记账系统启动成功!
               API文档: http://localhost:8081/doc.html
            ========================================
            """);
    }
}
