package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类 - 对应数据库 user 表
 *
 * 功能说明：
 * - 存储用户的基本信息（昵称、头像、状态等）
 * - 与 user_auth 表关联，支持多种登录方式
 *
 * 数据关系：
 * - 一对多：一个 User 可以有多个 UserAuth（手机号、邮箱、微信等）
 *
 * 状态说明：
 * - status: 0-正常, 1-禁用, 2-删除
 *
 * 软删除：
 * - deletedAt 不为 null 表示已删除
 *
 * @author QingLedger Team
 * @since 2026-04-04 完成回顾和注释
 */
@Data
@TableName("user")
public class User {

    /**
     * 用户ID（主键，自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像URL
     */
    private String avatar;

    /**
     * 用户状态
     * 0-正常, 1-禁用, 2-删除
     */
    private Integer status;

    /**
     * 软删除时间（不为null表示已删除）
     */
    private LocalDateTime deletedAt;

    /**
     * 创建时间（插入时自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（插入和更新时自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
