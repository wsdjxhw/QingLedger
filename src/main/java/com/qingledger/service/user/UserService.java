package com.qingledger.service.user;

import com.qingledger.entity.User;

public interface UserService {
    Long createUser(User user);
    User getUserById(Long userId);
    void updateProfile(Long userId, String nickname, String avatar);
}