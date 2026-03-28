package com.qingledger.service.auth;

import com.qingledger.common.Result;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 发送验证码
     * @param target 目标（手机号或邮箱）
     * @param type 类型（REGISTER/LOGIN/RESET_PASSWORD）
     * @return Result
     */
    Result<Void> sendCode(String target, String type);

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @param target 手机号或邮箱
     * @param code 验证码
     * @return Result
     */
    Result<String> register(String username, String password, String target, String code);

    /**
     * 用户登录
     * @param account 账号（用户名/手机号/邮箱）
     * @param password 密码
     * @return Result
     */
    Result<String> login(String account, String password);

    /**
     * 验证码登录
     * @param target 手机号或邮箱
     * @param code 验证码
     * @return Result
     */
    Result<String> loginWithCode(String target, String code);

    /**
     * 重置密码
     * @param target 手机号或邮箱
     * @param newPassword 新密码
     * @param code 验证码
     * @return Result
     */
    Result<Void> resetPassword(String target, String newPassword, String code);

    /**
     * 刷新令牌
     * @param refreshToken 刷新令牌
     * @return Result
     */
    Result<String> refreshToken(String refreshToken);
}
