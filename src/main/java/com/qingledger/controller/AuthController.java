package com.qingledger.controller;

import com.qingledger.common.Result;
import com.qingledger.dto.ClientInfo;
import com.qingledger.dto.request.*;
import com.qingledger.entity.User;
import com.qingledger.entity.UserAuth;
import com.qingledger.exception.AuthException;
import com.qingledger.mapper.UserMapper;
import com.qingledger.service.auth.AuthService;
import com.qingledger.service.auth.TokenService;
import com.qingledger.service.user.UserService;
import com.qingledger.service.userauth.UserAuthService;
import com.qingledger.utils.JwtUtil;
import com.qingledger.vo.BindingInfo;
import com.qingledger.vo.LoginResponse;
import com.qingledger.vo.UserInfo;
import com.qingledger.vo.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证控制器
 * 提供用户注册、登录、Token管理等认证相关接口
 */
@Tag(name = "认证接口", description = "用户注册、登录、Token管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";
    private static final int REFRESH_TOKEN_COOKIE_MAX_AGE = 7 * 24 * 60 * 60; // 7 天

    private final AuthService authService;
    private final UserAuthService userAuthService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final UserMapper userMapper;

    public AuthController(AuthService authService,
                         UserAuthService userAuthService,
                         UserService userService,
                         JwtUtil jwtUtil,
                         TokenService tokenService,
                         UserMapper userMapper) {
        this.authService = authService;
        this.userAuthService = userAuthService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
    }

    /**
     * 发送验证码
     */
    @Operation(summary = "发送验证码", description = "发送手机号或邮箱验证码")
    @PostMapping("/code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("发送验证码请求: target={}, type={}", request.getTarget(), request.getType());
        return authService.sendCode(request.getTarget(), request.getType());
    }

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "通过手机号或邮箱注册新用户")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request,
                                          HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
        log.info("用户注册请求: account={}", request.getAccount());

        Result<String> result = authService.register(
            request.getAccount(),
            request.getPassword(),
            request.getAccount(),
            request.getCode()
        );

        if (result.getCode() == 200) {
            String accessToken = result.getData();
            Long userId = jwtUtil.getUserId(accessToken);
            return buildLoginResponse(userId, httpRequest, httpResponse);
        } else {
            return Result.fail(result.getMessage());
        }
    }

    /**
     * 密码登录
     */
    @Operation(summary = "密码登录", description = "使用账号和密码登录")
    @PostMapping("/login/password")
    public Result<LoginResponse> loginByPassword(@RequestParam String account,
                                                  @RequestParam String password,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        log.info("用户登录请求: account={}", account);

        Result<String> result = authService.login(account, password);

        if (result.getCode() == 200) {
            String accessToken = result.getData();
            Long userId = jwtUtil.getUserId(accessToken);
            return buildLoginResponse(userId, httpRequest, httpResponse);
        } else {
            return Result.fail(result.getMessage());
        }
    }

    /**
     * 验证码登录
     */
    @Operation(summary = "验证码登录", description = "使用手机号或邮箱验证码登录")
    @PostMapping("/login/code")
    public Result<LoginResponse> loginByCode(@RequestParam String account,
                                              @RequestParam String code,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        log.info("验证码登录请求: target={}", account);

        Result<String> result = authService.loginWithCode(account, code);

        if (result.getCode() == 200) {
            String accessToken = result.getData();
            Long userId = jwtUtil.getUserId(accessToken);
            return buildLoginResponse(userId, httpRequest, httpResponse);
        } else {
            return Result.fail(result.getMessage());
        }
    }

    /**
     * 刷新Token
     */
    @Operation(summary = "刷新Token", description = "使用RefreshToken获取新的AccessToken")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(
            @CookieValue(value = "refreshToken", required = false) String cookieRefreshToken,
            @RequestParam(required = false) String paramRefreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        log.info("刷新Token请求");

        // 优先从 Cookie 获取(Web 端)
        String refreshToken = cookieRefreshToken;
        if (refreshToken == null || refreshToken.isEmpty()) {
            // 其次从参数获取(Mobile 端)
            refreshToken = paramRefreshToken;
        }

        if (refreshToken == null || refreshToken.isEmpty()) {
            return Result.fail("未提供 RefreshToken");
        }

        ClientInfo clientInfo = tokenService.extractClientInfo(httpRequest);

        Result<String> result = authService.refreshToken(refreshToken);

        if (result.getCode() == 200) {
            String accessToken = result.getData();
            Long userId = jwtUtil.getUserId(accessToken);

            TokenService.TokenPairResult tokens = tokenService.generateTokens(userId, clientInfo);

            LoginResponse response = new LoginResponse();
            response.setAccessToken(tokens.accessToken());
            response.setExpireIn(tokens.expireIn());

            // Web 端更新 Cookie
            if ("WEB".equalsIgnoreCase(clientInfo.getClientType())) {
                setRefreshTokenCookie(httpResponse, tokens.refreshToken());
            } else {
                response.setRefreshToken(tokens.refreshToken());
            }

            return Result.ok(response);
        } else {
            return Result.fail(result.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     */
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息", security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/user")
    public Result<UserInfoResponse> getUserInfo(HttpServletRequest request) {
        try {
            String accessToken = extractToken(request);
            Long userId = jwtUtil.getUserId(accessToken);

            log.info("获取用户信息: userId={}", userId);

            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.fail("用户不存在");
            }

            List<BindingInfo> bindings = userAuthService.getUserAuths(userId).stream()
                    .map(this::buildBindingInfo)
                    .collect(Collectors.toList());

            UserInfoResponse response = new UserInfoResponse();
            response.setId(userId);
            response.setNickname(user.getNickname());
            response.setAvatar(user.getAvatar());
            response.setBindings(bindings);

            return Result.ok(response);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return Result.fail("获取用户信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取绑定方式列表
     */
    @Operation(summary = "获取绑定方式列表", description = "获取当前用户绑定的所有登录方式", security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/bindings")
    public Result<List<BindingInfo>> getBindings(HttpServletRequest request) {
        try {
            String accessToken = extractToken(request);
            Long userId = jwtUtil.getUserId(accessToken);

            List<BindingInfo> bindings = userAuthService.getUserAuths(userId).stream()
                    .map(this::buildBindingInfo)
                    .collect(Collectors.toList());

            return Result.ok(bindings);
        } catch (Exception e) {
            log.error("获取绑定方式列表失败", e);
            return Result.fail("获取绑定方式列表失败: " + e.getMessage());
        }
    }

    /**
     * 绑定邮箱
     */
    @Operation(summary = "绑定邮箱", description = "绑定邮箱到当前账号", security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/bind/email")
    public Result<Void> bindEmail(HttpServletRequest request, @Valid @RequestBody BindEmailRequest req) {
        try {
            String accessToken = extractToken(request);
            Long userId = jwtUtil.getUserId(accessToken);
            log.info("绑定邮箱请求: userId={}, email={}", userId, req.getEmail());

            // TODO: 实现绑定邮箱逻辑
            return Result.fail("功能开发中");
        } catch (Exception e) {
            log.error("绑定邮箱失败", e);
            return Result.fail("绑定邮箱失败: " + e.getMessage());
        }
    }

    /**
     * 解绑登录方式
     */
    @Operation(summary = "解绑登录方式", description = "解绑手机号或邮箱", security = @SecurityRequirement(name = "JWT"))
    @DeleteMapping("/unbind/{type}")
    public Result<Void> unbind(HttpServletRequest request, @PathVariable String type) {
        try {
            String accessToken = extractToken(request);
            Long userId = jwtUtil.getUserId(accessToken);
            log.info("解绑登录方式请求: userId={}, type={}", userId, type);

            // TODO: 实现解绑逻辑
            return Result.fail("功能开发中");
        } catch (Exception e) {
            log.error("解绑失败", e);
            return Result.fail("解绑失败: " + e.getMessage());
        }
    }

    /**
     * 修改密码
     */
    @Operation(summary = "修改密码", description = "已登录用户修改密码", security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/password/change")
    public Result<Void> changePassword(HttpServletRequest request, @Valid @RequestBody ChangePasswordRequest req) {
        try {
            String accessToken = extractToken(request);
            Long userId = jwtUtil.getUserId(accessToken);
            log.info("修改密码请求: userId={}", userId);

            // TODO: 实现修改密码逻辑
            return Result.fail("功能开发中");
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return Result.fail("修改密码失败: " + e.getMessage());
        }
    }

    /**
     * 重置密码
     */
    @Operation(summary = "重置密码", description = "通过验证码重置密码")
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("重置密码请求: account={}", request.getAccount());
        return authService.resetPassword(
            request.getAccount(),
            request.getNewPassword(),
            request.getCode()
        );
    }

    /**
     * 退出登录
     */
    @Operation(summary = "退出登录", description = "废除RefreshToken", security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            String accessToken = extractToken(request);
            Long userId = jwtUtil.getUserId(accessToken);
            String tokenId = jwtUtil.getTokenId(accessToken);

            tokenService.revokeRefreshToken(userId, tokenId);

            // 清除 Cookie
            clearRefreshTokenCookie(response);

            log.info("用户登出: userId={}", userId);
            return Result.ok();
        } catch (Exception e) {
            log.error("登出失败", e);
            return Result.fail("登出失败: " + e.getMessage());
        }
    }

    // ========== 私有方法 ==========

    /**
     * 构建登录响应(带设备信息)
     */
    private Result<LoginResponse> buildLoginResponse(Long userId,
                                                      HttpServletRequest httpRequest,
                                                      HttpServletResponse httpResponse) {
        ClientInfo clientInfo = tokenService.extractClientInfo(httpRequest);
        TokenService.TokenPairResult tokens = tokenService.generateTokens(userId, clientInfo);

        User user = userMapper.selectById(userId);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(tokens.accessToken());
        response.setExpireIn(tokens.expireIn());
        response.setUser(buildUserInfo(user));

        // Web 端返回 HttpOnly Cookie
        if ("WEB".equalsIgnoreCase(clientInfo.getClientType())) {
            setRefreshTokenCookie(httpResponse, tokens.refreshToken());
        } else {
            // Mobile 端在响应体中返回
            response.setRefreshToken(tokens.refreshToken());
        }

        return Result.ok(response);
    }

    /**
     * 设置 RefreshToken HttpOnly Cookie
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(REFRESH_TOKEN_COOKIE_PATH);
        cookie.setMaxAge(REFRESH_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    /**
     * 清除 RefreshToken Cookie
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(REFRESH_TOKEN_COOKIE_PATH);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 从请求中提取Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new AuthException(1003, "未提供有效的认证令牌");
    }

    /**
     * 构建用户信息
     */
    private UserInfo buildUserInfo(User user) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(user.getId());
        userInfo.setNickname(user.getNickname());
        userInfo.setAvatar(user.getAvatar());
        return userInfo;
    }

    /**
     * 构建绑定信息
     */
    private BindingInfo buildBindingInfo(UserAuth auth) {
        BindingInfo info = new BindingInfo();
        info.setType(auth.getAuthType().toLowerCase());
        info.setIdentifier(maskIdentifier(auth.getIdentifier(), auth.getAuthType()));
        info.setIsPrimary(auth.getIsPrimary());
        info.setVerified(auth.getVerified());
        info.setBindAt(auth.getBindAt());
        return info;
    }

    /**
     * 脱敏处理
     */
    private String maskIdentifier(String identifier, String type) {
        if ("PHONE".equalsIgnoreCase(type)) {
            return identifier.substring(0, 3) + "****" + identifier.substring(7);
        } else {
            int atIndex = identifier.indexOf("@");
            String username = identifier.substring(0, atIndex);
            String domain = identifier.substring(atIndex);
            if (username.length() <= 2) {
                return "*" + domain;
            }
            return username.charAt(0) + "***" + domain;
        }
    }
}
