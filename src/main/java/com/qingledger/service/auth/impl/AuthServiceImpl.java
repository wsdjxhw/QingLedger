package com.qingledger.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qingledger.entity.User;
import com.qingledger.entity.UserAuth;
import com.qingledger.common.Result;
import com.qingledger.exception.AuthException;
import com.qingledger.mapper.UserAuthMapper;
import com.qingledger.mapper.UserMapper;
import com.qingledger.service.auth.AuthService;
import com.qingledger.service.auth.TokenService;
import com.qingledger.service.auth.VerificationService;
import com.qingledger.service.user.UserService;
import com.qingledger.service.userauth.UserAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务实现
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final VerificationService verificationService;
    private final TokenService tokenService;
    private final UserAuthService userAuthService;
    private final UserAuthMapper userAuthMapper;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    // 认证类型常量
    private static final String AUTH_TYPE_USERNAME = "USERNAME";
    private static final String AUTH_TYPE_PHONE = "PHONE";
    private static final String AUTH_TYPE_EMAIL = "EMAIL";

    // 用户状态常量
    private static final Integer USER_STATUS_ACTIVE = 1;
    private static final Integer USER_STATUS_DISABLED = 0;

    // 手机号格式校验正则
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    // 邮箱格式校验正则
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$";

    public AuthServiceImpl(UserService userService,
                          VerificationService verificationService,
                          TokenService tokenService,
                          UserAuthService userAuthService,
                          UserAuthMapper userAuthMapper,
                          UserMapper userMapper,
                          BCryptPasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.verificationService = verificationService;
        this.tokenService = tokenService;
        this.userAuthService = userAuthService;
        this.userAuthMapper = userAuthMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Result<Void> sendCode(String target, String type) {
        log.info("发送验证码: target={}, type={}", target, type);

        // 校验目标格式
        validateTargetFormat(target);

        // 检查用户是否存在（注册时需要检查，登录时也需要检查）
        if ("REGISTER".equalsIgnoreCase(type)) {
            // 注册时，如果用户已存在则报错
            if (existsByPhoneOrEmail(target)) {
                return Result.fail("该手机号或邮箱已被注册");
            }
        } else if ("LOGIN".equalsIgnoreCase(type) || "RESET_PASSWORD".equalsIgnoreCase(type)) {
            // 登录或重置密码时，如果用户不存在则报错
            if (!existsByPhoneOrEmail(target)) {
                return Result.fail("该手机号或邮箱未注册");
            }
        }

        // 根据 target 判断验证码类型（PHONE 或 EMAIL）
        String verificationType;
        if (target.matches(PHONE_PATTERN)) {
            verificationType = AUTH_TYPE_PHONE;
        } else if (target.matches(EMAIL_PATTERN)) {
            verificationType = AUTH_TYPE_EMAIL;
        } else {
            return Result.fail("不支持的验证码目标类型");
        }

        // 发送验证码
        try {
            verificationService.sendCode(verificationType, target);
            return Result.ok();
        } catch (Exception e) {
            log.error("验证码发送失败", e);
            return Result.fail("验证码发送失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<String> register(String username, String password, String target, String code) {
        log.info("用户注册: username={}, target={}", username, target);

        // 校验目标格式
        validateTargetFormat(target);

        // 校验用户名
        if (username == null || username.trim().isEmpty()) {
            return Result.fail("用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 20) {
            return Result.fail("用户名长度必须在3-20个字符之间");
        }

        // 校验密码
        if (password == null || password.length() < 6 || password.length() > 20) {
            return Result.fail("密码长度必须在6-20个字符之间");
        }

        // 校验验证码
        try {
            // 根据 target 判断验证码类型（PHONE 或 EMAIL）
            String verificationType;
            if (target.matches(PHONE_PATTERN)) {
                verificationType = AUTH_TYPE_PHONE;
            } else if (target.matches(EMAIL_PATTERN)) {
                verificationType = AUTH_TYPE_EMAIL;
            } else {
                return Result.fail("不支持的验证码目标类型");
            }

            if (!verificationService.verifyCode(verificationType, target, code)) {
                return Result.fail("验证码错误或已过期");
            }
        } catch (Exception e) {
            log.error("验证码校验失败", e);
            return Result.fail("验证码校验失败: " + e.getMessage());
        }

        // 检查用户名是否已存在
        if (existsByUsername(username)) {
            return Result.fail("用户名已存在");
        }

        // 检查手机号或邮箱是否已存在
        if (existsByPhoneOrEmail(target)) {
            return Result.fail("该手机号或邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setNickname(username);
        user.setStatus(USER_STATUS_ACTIVE);

        userMapper.insert(user);

        // 创建用户认证信息
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(user.getId());
        userAuth.setPassword(passwordEncoder.encode(password));
        userAuth.setVerified(true);
        userAuth.setIsPrimary(true);
        userAuth.setBindAt(LocalDateTime.now());

        // 根据目标类型设置认证类型和标识
        String authType;
        if (target.matches(PHONE_PATTERN)) {
            authType = AUTH_TYPE_PHONE;
        } else if (target.matches(EMAIL_PATTERN)) {
            authType = AUTH_TYPE_EMAIL;
        } else {
            authType = AUTH_TYPE_USERNAME;
        }

        userAuth.setAuthType(authType);
        userAuth.setIdentifier(target);

        userAuthMapper.insert(userAuth);

        // 生成令牌
        TokenService.TokenPairResult tokens = tokenService.generateTokens(user.getId());

        log.info("用户注册成功: userId={}", user.getId());
        return Result.ok(tokens.accessToken());
    }

    @Override
    public Result<String> login(String account, String password) {
        log.info("用户登录: account={}", account);

        if (account == null || account.trim().isEmpty()) {
            return Result.fail("账号不能为空");
        }
        if (password == null || password.isEmpty()) {
            return Result.fail("密码不能为空");
        }

        // 查找用户认证信息
        UserAuth userAuth = findUserAuthByAccount(account);
        if (userAuth == null) {
            return Result.fail("账号或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, userAuth.getPassword())) {
            return Result.fail("账号或密码错误");
        }

        // 检查用户状态
        User user = userMapper.selectById(userAuth.getUserId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (!USER_STATUS_ACTIVE.equals(user.getStatus())) {
            return Result.fail("用户已被禁用");
        }

        // 生成令牌
        TokenService.TokenPairResult tokens = tokenService.generateTokens(userAuth.getUserId());

        log.info("用户登录成功: userId={}", userAuth.getUserId());
        return Result.ok(tokens.accessToken());
    }

    @Override
    public Result<String> loginWithCode(String target, String code) {
        log.info("验证码登录: target={}", target);

        // 校验目标格式
        validateTargetFormat(target);

        // 校验验证码
        try {
            // 根据 target 判断验证码类型（PHONE 或 EMAIL）
            String verificationType;
            if (target.matches(PHONE_PATTERN)) {
                verificationType = AUTH_TYPE_PHONE;
            } else if (target.matches(EMAIL_PATTERN)) {
                verificationType = AUTH_TYPE_EMAIL;
            } else {
                return Result.fail("不支持的验证码目标类型");
            }

            if (!verificationService.verifyCode(verificationType, target, code)) {
                return Result.fail("验证码错误或已过期");
            }
        } catch (Exception e) {
            log.error("验证码校验失败", e);
            return Result.fail("验证码校验失败: " + e.getMessage());
        }

        // 查找用户
        UserAuth userAuth = findUserAuthByPhoneOrEmail(target);
        if (userAuth == null) {
            return Result.fail("该手机号或邮箱未注册");
        }

        // 检查用户状态
        User user = userMapper.selectById(userAuth.getUserId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (!USER_STATUS_ACTIVE.equals(user.getStatus())) {
            return Result.fail("用户已被禁用");
        }

        // 生成令牌
        TokenService.TokenPairResult tokens = tokenService.generateTokens(userAuth.getUserId());

        log.info("验证码登录成功: userId={}", userAuth.getUserId());
        return Result.ok(tokens.accessToken());
    }

    @Override
    @Transactional
    public Result<Void> resetPassword(String target, String newPassword, String code) {
        log.info("重置密码: target={}", target);

        // 校验目标格式
        validateTargetFormat(target);

        // 校验新密码
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            return Result.fail("密码长度必须在6-20个字符之间");
        }

        // 校验验证码
        try {
            // 根据 target 判断验证码类型（PHONE 或 EMAIL）
            String verificationType;
            if (target.matches(PHONE_PATTERN)) {
                verificationType = AUTH_TYPE_PHONE;
            } else if (target.matches(EMAIL_PATTERN)) {
                verificationType = AUTH_TYPE_EMAIL;
            } else {
                return Result.fail("不支持的验证码目标类型");
            }

            if (!verificationService.verifyCode(verificationType, target, code)) {
                return Result.fail("验证码错误或已过期");
            }
        } catch (Exception e) {
            log.error("验证码校验失败", e);
            return Result.fail("验证码校验失败: " + e.getMessage());
        }

        // 查找用户认证信息
        UserAuth userAuth = findUserAuthByPhoneOrEmail(target);
        if (userAuth == null) {
            return Result.fail("该手机号或邮箱未注册");
        }

        // 更新密码
        userAuth.setPassword(passwordEncoder.encode(newPassword));
        userAuthMapper.updateById(userAuth);

        log.info("密码重置成功: userId={}", userAuth.getUserId());
        return Result.ok();
    }

    @Override
    public Result<String> refreshToken(String refreshToken) {
        log.info("刷新令牌");

        try {
            TokenService.TokenPairResult tokens = tokenService.refreshAccessToken(refreshToken);
            return Result.ok(tokens.accessToken());
        } catch (Exception e) {
            log.error("刷新令牌失败", e);
            return Result.fail("刷新令牌失败: " + e.getMessage());
        }
    }

    /**
     * 校验目标格式（手机号或邮箱）
     */
    private void validateTargetFormat(String target) {
        if (target == null || target.trim().isEmpty()) {
            throw new AuthException(1001, "目标不能为空");
        }

        boolean isPhone = target.matches(PHONE_PATTERN);
        boolean isEmail = target.matches(EMAIL_PATTERN);

        if (!isPhone && !isEmail) {
            throw new AuthException(1002, "请输入有效的手机号或邮箱");
        }
    }

    /**
     * 检查用户名是否存在
     */
    private boolean existsByUsername(String username) {
        QueryWrapper<UserAuth> wrapper = new QueryWrapper<>();
        wrapper.eq("auth_type", AUTH_TYPE_USERNAME)
               .eq("identifier", username);
        return userAuthMapper.selectCount(wrapper) > 0;
    }

    /**
     * 检查手机号或邮箱是否存在
     */
    private boolean existsByPhoneOrEmail(String phoneOrEmail) {
        QueryWrapper<UserAuth> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("auth_type", AUTH_TYPE_PHONE)
                          .eq("identifier", phoneOrEmail)
                          .or()
                          .eq("auth_type", AUTH_TYPE_EMAIL)
                          .eq("identifier", phoneOrEmail));
        return userAuthMapper.selectCount(wrapper) > 0;
    }

    /**
     * 通过账号查找用户认证信息
     */
    private UserAuth findUserAuthByAccount(String account) {
        QueryWrapper<UserAuth> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("auth_type", AUTH_TYPE_USERNAME)
                          .eq("identifier", account)
                          .or()
                          .eq("auth_type", AUTH_TYPE_PHONE)
                          .eq("identifier", account)
                          .or()
                          .eq("auth_type", AUTH_TYPE_EMAIL)
                          .eq("identifier", account));
        return userAuthMapper.selectOne(wrapper);
    }

    /**
     * 通过手机号或邮箱查找用户认证信息
     */
    private UserAuth findUserAuthByPhoneOrEmail(String phoneOrEmail) {
        QueryWrapper<UserAuth> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("auth_type", AUTH_TYPE_PHONE)
                          .eq("identifier", phoneOrEmail)
                          .or()
                          .eq("auth_type", AUTH_TYPE_EMAIL)
                          .eq("identifier", phoneOrEmail));
        return userAuthMapper.selectOne(wrapper);
    }
}
