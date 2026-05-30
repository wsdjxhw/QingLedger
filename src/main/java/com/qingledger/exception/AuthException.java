package com.qingledger.exception;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {
    private final int code;

    // 异常码常量
    public static final int TOKEN_TYPE_ERROR = 1008;
    public static final int REFRESH_TOKEN_INVALID = 1009;
    public static final int REFRESH_TOKEN_EXPIRED = 1010;
    public static final int TOKEN_INFO_PARSE_ERROR = 1011;
    public static final int DEVICE_MISMATCH = 1012;

    public AuthException(int code, String message) {
        super(message);
        this.code = code;
    }
}
