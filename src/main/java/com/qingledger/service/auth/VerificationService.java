package com.qingledger.service.auth;

/**
 * 验证码服务 - 负责手机号和邮箱验证码的发送、验证和管理
 *
 * 功能概述：
 * - 发送验证码到手机号或邮箱
 * - 验证用户输入的验证码是否正确
 * - 管理验证码的有效期和限制
 *
 * 安全特性：
 * - 验证码1分钟内过期
 * - 1分钟内只能发送1次
 * - 每天最多发送15次
 * - 验证失败5次后验证码失效
 * - 使用Lua脚本保证Redis操作的原子性
 *
 * 支持的验证码类型：
 * - PHONE: 手机号验证码
 * - EMAIL: 邮箱验证码
 *
 * @author QingLedger Team
 * @since 2026-04-04 完成回顾和注释
 */
public interface VerificationService {

    /**
     * 发送验证码
     *
     * 功能：
     * - 生成6位数字验证码
     * - 存储到Redis（1分钟过期）
     * - 检查发送频率限制
     * - 开发环境：在控制台打印验证码
     *
     * 限制规则：
     * - 1分钟内只能发送1次
     * - 每天最多发送15次
     *
     * @param type 验证码类型（PHONE/EMAIL）
     * @param target 发送目标（手机号/邮箱）
     * @throws //VerificationException 1001-发送过于频繁, 1002-达到日上限
     */
    void sendCode(String type, String target);

    /**
     * 验证验证码
     *
     * 功能：
     * - 从Redis获取存储的验证码
     * - 与用户输入的验证码比对
     * - 验证成功后删除验证码（一次性使用）
     * - 记录验证失败次数
     *
     * 失败处理：
     * - 验证失败5次后，验证码失效
     *
     * @param type 验证码类型（PHONE/EMAIL）
     * @param target 发送目标（手机号/邮箱）
     * @param code 用户输入的验证码
     * @return true-验证成功
     * @throws //VerificationException 1005-验证码错误, 1006-验证码失效, 1015-失败次数过多
     */
    boolean verifyCode(String type, String target, String code);

    /**
     * 删除验证码
     *
     * 功能：从Redis删除指定的验证码
     *
     * 使用场景：
     * - 验证成功后删除（一次性使用）
     * - 验证失败次数过多时删除
     * - 用户重新发送验证码时删除旧的
     *
     * @param type 验证码类型（PHONE/EMAIL）
     * @param target 发送目标（手机号/邮箱）
     */
    void deleteCode(String type, String target);
}
