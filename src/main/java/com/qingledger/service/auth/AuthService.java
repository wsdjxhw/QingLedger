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
     * @return Result 返回用户ID
     */
    Result<Long> register(String username, String password, String target, String code);

    /**
     * 用户登录
     * @param account 账号（用户名/手机号/邮箱）
     * @param password 密码
     * @return Result 返回用户ID
     */
    Result<Long> login(String account, String password);

    /**
     * 验证码登录
     * @param target 手机号或邮箱
     * @param code 验证码
     * @return Result 返回用户ID
     */
    Result<Long> loginWithCode(String target, String code);

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

    /**
     * 绑定邮箱
     * @param userId 用户ID
     * @param email 邮箱
     * @param code 验证码
     * @return Result
     */
    Result<Void> bindEmail(Long userId, String email, String code);

    /**
     * 绑定手机号
     * @param userId 用户ID
     * @param phone 手机号
     * @param code 验证码
     * @return Result
     */
    Result<Void> bindPhone(Long userId, String phone, String code);

    /**
     * 解绑登录方式
     * @param userId 用户ID
     * @param authType 认证类型（PHONE/EMAIL）
     * @param password 用户当前密码（用于二次身份验证）
     * @return Result
     */
    Result<Void> unbind(Long userId, String authType, String password);

    /**
     * 切换主账号登录方式
     * @param userId 用户ID
     * @param authType 目标认证类型（PHONE/EMAIL）
     * @return Result
     */
    Result<Void> switchPrimary(Long userId, String authType);

    /**
     * 修改密码
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return Result
     */
    Result<Void> changePassword(Long userId, String oldPassword, String newPassword);
}
