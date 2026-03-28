package com.qingledger.service.auth;

public interface VerificationService {
    void sendCode(String type, String target);
    boolean verifyCode(String type, String target, String code);
    void deleteCode(String type, String target);
}
