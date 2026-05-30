package com.qingledger.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.qingledger.entity.User;
import com.qingledger.mapper.UserMapper;
import com.qingledger.service.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void updateProfile(Long userId, String nickname, String avatar) {
        // 均无需更新，直接返回
        if (nickname == null && avatar == null) {
            return;
        }

        User user = new User();
        user.setId(userId);

        boolean needUpdate = false;

        if (nickname != null) {
            user.setNickname(nickname);
            needUpdate = true;
        }

        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatar(avatar);
            needUpdate = true;
        }

        // 普通字段更新
        if (needUpdate) {
            userMapper.updateById(user);
        }

        // avatar 为空字符串：单独处理，显式写 null
        if (avatar != null && avatar.isEmpty()) {
            userMapper.update(null,
                    new UpdateWrapper<User>()
                            .set("avatar", null)
                            .eq("id", userId));
        }
    }
}