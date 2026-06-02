package com.qingledger.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.qingledger.common.BusinessException;

public enum LedgerType {
    PERSONAL("personal"),
    SHARED("shared");

    @EnumValue
    private final String code;

    LedgerType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static LedgerType fromCode(String code) {
        if (code == null) return null;
        for (LedgerType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        throw new BusinessException(400, "ledger type 必须为 personal 或 shared");
    }
}
