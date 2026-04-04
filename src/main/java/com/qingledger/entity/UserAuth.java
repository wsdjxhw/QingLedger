package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户认证方式实体类 - 对应数据库 user_auth 表
 *
 * 功能说明：
 * - 存储用户的登录方式（手机号、邮箱、微信等）
 * - 支持一个用户绑定多种登录方式
 * - 密码加密存储（BCrypt）
 *
 * 数据关系：
 * - 多对一：多个 UserAuth 属于一个 User（通过 userId 关联）
 *
 * 认证类型（authType）：
 * - PHONE: 手机号登录
 * - EMAIL: 邮箱登录
 * - WECHAT: 微信登录（待实现）
 *
 * 主要标识（identifier）：
 * - PHONE: 手机号
 * - EMAIL: 邮箱地址
 * - WECHAT: 微信OpenID
 *
 * @author QingLedger Team
 * @since 2026-04-04 完成回顾和注释
 */
@Data
@TableName("user_auth")
public class UserAuth {

    /**
     * 认证记录ID（主键，自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（外键，关联 user 表）
     */
    private Long userId;

    /**
     * 认证类型
     * PHONE-手机号, EMAIL-邮箱, WECHAT-微信
     */
    private String authType;

    /**
     * 认证标识（账号）
     * 手机号/邮箱地址/微信OpenID
     */
    private String identifier;

    /**
     * 密码（BCrypt加密存储）
     * 微信登录时为空
     */
    private String password;

    /**
     * 是否已验证
     * true-已验证（手机号/邮箱已验证）, false-未验证
     */
    private Boolean verified;

    /**
     * 是否为主账号
     * true-主账号, false-辅助账号
     * 一个用户只能有一个主账号
     */
    private Boolean isPrimary;

    /**
     * 绑定时间（插入时自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime bindAt;
}
