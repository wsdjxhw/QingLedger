package com.qingledger.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.qingledger.common.BusinessException;

public enum CategoryType {
    INCOME("income"),
    EXPENSE("expense");

    @EnumValue
    private final String code;

    CategoryType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static CategoryType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CategoryType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new BusinessException(400, "type 必须为 income 或 expense");
    }
}
