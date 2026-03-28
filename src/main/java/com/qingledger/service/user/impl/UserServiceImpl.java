package com.qingledger.service.user.impl;

import com.qingledger.entity.User;
import com.qingledger.mapper.UserMapper;
import com.qingledger.service.user.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Long createUser(User user) {
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public void updateNickname(Long userId, String nickname) {
        User user = new User();
        user.setId(userId);
        user.setNickname(nickname);
        userMapper.updateById(user);
    }

    @Override
    public void updateAvatar(Long userId, String avatar) {
        User user = new User();
        user.setId(userId);
        user.setAvatar(avatar);
        userMapper.updateById(user);
    }
}