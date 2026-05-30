package com.qingledger.controller;

import com.qingledger.common.Result;
import com.qingledger.dto.request.UpdateProfileRequest;
import com.qingledger.entity.User;
import com.qingledger.entity.UserAuth;
import com.qingledger.exception.AuthException;
import com.qingledger.mapper.UserMapper;
import com.qingledger.service.user.UserService;
import com.qingledger.service.userauth.UserAuthService;
import com.qingledger.utils.JwtUtil;
import com.qingledger.vo.BindingInfo;
import com.qingledger.vo.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "用户资料接口", description = "用户资料管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final UserAuthService userAuthService;
    private final UserMapper userMapper;

    public UserController(JwtUtil jwtUtil,
                          UserService userService,
                          UserAuthService userAuthService,
                          UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userAuthService = userAuthService;
        this.userMapper = userMapper;
    }

    @Operation(summary = "获取当前用户资料", description = "获取当前登录用户的详细资料和绑定列表", security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/profile")
    public Result<UserInfoResponse> getProfile(HttpServletRequest request) {
        try {
            String accessToken = extractToken(request);
            Long userId = jwtUtil.getUserId(accessToken);

            log.info("获取用户资料: userId={}", userId);

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
            log.error("获取用户资料失败", e);
            return Result.fail("获取用户资料失败: " + e.getMessage());
        }
    }

    @Operation(summary = "修改用户资料", description = "修改昵称和/或头像", security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/profile")
    public Result<Void> updateProfile(HttpServletRequest request,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        try {
            String accessToken = extractToken(request);
            Long userId = jwtUtil.getUserId(accessToken);

            log.info("修改用户资料请求: userId={}", userId);
            userService.updateProfile(userId, req.getNickname(), req.getAvatar());

            return Result.ok();
        } catch (Exception e) {
            log.error("修改用户资料失败", e);
            return Result.fail("修改用户资料失败: " + e.getMessage());
        }
    }

    // ========== 私有方法 ==========

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new AuthException(1003, "未提供有效的认证令牌");
    }

    private BindingInfo buildBindingInfo(UserAuth auth) {
        BindingInfo info = new BindingInfo();
        info.setType(auth.getAuthType().toLowerCase());
        info.setIdentifier(maskIdentifier(auth.getIdentifier(), auth.getAuthType()));
        info.setIsPrimary(auth.getIsPrimary());
        info.setVerified(auth.getVerified());
        info.setBindAt(auth.getBindAt());
        return info;
    }

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
