package com.qingledger.controller;

import com.qingledger.common.Result;
import com.qingledger.dto.request.*;
import com.qingledger.dto.response.LoginResponse;
import com.qingledger.entity.User;
import com.qingledger.exception.AuthException;
import com.qingledger.mapper.UserMapper;
import com.qingledger.service.auth.AuthService;
import com.qingledger.service.user.UserService;
import com.qingledger.service.userauth.UserAuthService;
import com.qingledger.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserAuthService userAuthService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public AuthController(AuthService authService,
                         UserAuthService userAuthService,
                         UserService userService,
                         JwtUtil jwtUtil,
                         UserMapper userMapper) {
        this.authService = authService;
        this.userAuthService = userAuthService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    /**
     * 发送验证码
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody SendCodeRequest request) {
        log.info("发送验证码请求: target={}, type={}", request.getTarget(), request.getType());
        return authService.sendCode(request.getTarget(), request.getType());
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterRequest request) {
        log.info("用户注册请求: account={}", request.getAccount());
        // RegisterRequest uses account for both username and phone/email
        return authService.register(
            request.getAccount(),
            request.getPassword(),
            request.getAccount(),
            request.getCode()
        );
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("用户登录请求: account={}", request.getAccount());

        Result<String> result = authService.login(request.getAccount(), request.getPassword());

        if (result.getCode() == 200) {
            LoginResponse response = new LoginResponse();
            response.setToken(result.getData());
            return Result.ok(response);
        } else {
            return Result.fail(result.getMessage());
        }
    }

    /**
     * 验证码登录
     */
    @PostMapping("/login-with-code")
    public Result<LoginResponse> loginWithCode(@RequestBody LoginWithCodeRequest request) {
        log.info("验证码登录请求: target={}", request.getTarget());

        Result<String> result = authService.loginWithCode(request.getTarget(), request.getCode());

        if (result.getCode() == 200) {
            LoginResponse response = new LoginResponse();
            response.setToken(result.getData());
            return Result.ok(response);
        } else {
            return Result.fail(result.getMessage());
        }
    }

    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        log.info("重置密码请求: account={}", request.getAccount());
        return authService.resetPassword(
            request.getAccount(),
            request.getNewPassword(),
            request.getCode()
        );
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh-token")
    public Result<String> refreshToken(@RequestBody RefreshTokenRequest request) {
        log.info("刷新令牌请求");
        return authService.refreshToken(request.getRefreshToken());
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public Result<User> getUserInfo(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            Long userId = jwtUtil.getUserId(token);

            log.info("获取用户信息: userId={}", userId);

            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.fail("用户不存在");
            }

            return Result.ok(user);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return Result.fail("获取用户信息失败: " + e.getMessage());
        }
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            log.info("用户登出");

            // TODO: 将令牌加入黑名单（如果需要）
            return Result.ok();
        } catch (Exception e) {
            log.error("登出失败", e);
            return Result.fail("登出失败: " + e.getMessage());
        }
    }

    /**
     * 从请求中提取令牌
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new AuthException(1003, "未提供有效的认证令牌");
    }
}
