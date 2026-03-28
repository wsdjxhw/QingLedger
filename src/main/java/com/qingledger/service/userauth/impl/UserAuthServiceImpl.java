package com.qingledger.service.userauth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingledger.entity.UserAuth;
import com.qingledger.exception.AuthException;
import com.qingledger.mapper.UserAuthMapper;
import com.qingledger.service.userauth.UserAuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAuthServiceImpl implements UserAuthService {

    private final UserAuthMapper userAuthMapper;

    public UserAuthServiceImpl(UserAuthMapper userAuthMapper) {
        this.userAuthMapper = userAuthMapper;
    }

    @Override
    @Transactional
    public Long bindAuth(Long userId, String authType, String identifier, String password) {
        // 检查标识符是否已被绑定
        Long existingUserId = getUserIdByIdentifier(authType, identifier);
        if (existingUserId != null) {
            String typeName = "phone".equals(authType) ? "手机号" : "邮箱";
            if (existingUserId.equals(userId)) {
                throw new AuthException(1001, typeName + "已绑定");
            }
            throw new AuthException(1001, typeName + "已被使用");
        }

        // 创建绑定
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(userId);
        userAuth.setAuthType(authType);
        userAuth.setIdentifier(identifier);
        userAuth.setPassword(password);
        userAuth.setVerified(true);
        userAuth.setIsPrimary(false);

        // 如果是第一个绑定,设为主要
        List<UserAuth> auths = getUserAuths(userId);
        if (auths.isEmpty()) {
            userAuth.setIsPrimary(true);
        }

        userAuthMapper.insert(userAuth);
        return userAuth.getId();
    }

    @Override
    @Transactional
    public void unbindAuth(Long userId, String authType) {
        List<UserAuth> auths = getUserAuths(userId);
        if (auths.size() == 1) {
            throw new AuthException(1014, "至少保留一种登录方式");
        }

        UserAuth toUnbind = auths.stream()
                .filter(auth -> auth.getAuthType().equals(authType))
                .findFirst()
                .orElseThrow(() -> new AuthException(1014, "未找到该认证方式"));

        if (toUnbind.getIsPrimary()) {
            UserAuth newPrimary = auths.stream()
                    .filter(auth -> !auth.getId().equals(toUnbind.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AuthException(1014, "解绑失败"));
            newPrimary.setIsPrimary(true);
            userAuthMapper.updateById(newPrimary);
        }

        userAuthMapper.deleteById(toUnbind.getId());
    }

    @Override
    public Long getUserIdByIdentifier(String authType, String identifier) {
        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAuth::getAuthType, authType)
                .eq(UserAuth::getIdentifier, identifier);
        UserAuth userAuth = userAuthMapper.selectOne(wrapper);
        return userAuth != null ? userAuth.getUserId() : null;
    }

    @Override
    public List<UserAuth> getUserAuths(Long userId) {
        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAuth::getUserId, userId);
        return userAuthMapper.selectList(wrapper);
    }

    @Override
    public UserAuth getPrimaryAuth(Long userId) {
        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAuth::getUserId, userId)
                .eq(UserAuth::getIsPrimary, true);
        return userAuthMapper.selectOne(wrapper);
    }
}