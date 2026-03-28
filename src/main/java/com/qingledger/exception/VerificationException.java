package com.qingledger.exception;

import lombok.Getter;

@Getter
public class VerificationException extends RuntimeException {
    private final int code;

    public VerificationException(int code, String message) {
        super(message);
        this.code = code;
    }
}
