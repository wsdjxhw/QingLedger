package com.qingledger.service.userauth;

import com.qingledger.entity.UserAuth;
import java.util.List;

public interface UserAuthService {
    Long bindAuth(Long userId, String authType, String identifier, String password);
    void unbindAuth(Long userId, String authType);
    Long getUserIdByIdentifier(String authType, String identifier);
    List<UserAuth> getUserAuths(Long userId);
    UserAuth getPrimaryAuth(Long userId);
}