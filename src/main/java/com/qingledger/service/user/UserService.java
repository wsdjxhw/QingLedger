package com.qingledger.service.user;

import com.qingledger.entity.User;

public interface UserService {
    Long createUser(User user);
    User getUserById(Long userId);
    void updateNickname(Long userId, String nickname);
    void updateAvatar(Long userId, String avatar);
}