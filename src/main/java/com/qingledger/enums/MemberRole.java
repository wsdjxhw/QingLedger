package com.qingledger.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.qingledger.common.BusinessException;

public enum MemberRole {
    OWNER("owner"),
    ADMIN("admin"),
    MEMBER("member");

    @EnumValue
    private final String code;

    MemberRole(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static MemberRole fromCode(String code) {
        if (code == null) return null;
        for (MemberRole r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        throw new BusinessException(400, "角色必须为 owner / admin / member");
    }
}
