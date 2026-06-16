package com.qingledger.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.qingledger.common.BusinessException;

public enum TransactionType {
    INCOME("income"),
    EXPENSE("expense");

    @EnumValue
    private final String code;

    TransactionType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TransactionType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TransactionType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new BusinessException(400, "type 必须为 income 或 expense");
    }
}
