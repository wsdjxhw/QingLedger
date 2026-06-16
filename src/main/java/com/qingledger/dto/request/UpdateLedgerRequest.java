package com.qingledger.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateLedgerRequest {

    @Size(max = 50, message = "账本名称长度不能超过50个字符")
    private String name;

    @Size(max = 200, message = "账本描述长度不能超过200个字符")
    private String description;

    @Size(max = 50, message = "图标标识长度不能超过50个字符")
    private String icon;

    @Pattern(regexp = "^(#[A-Fa-f0-9]{6})?$", message = "颜色格式不正确，请使用 #RRGGBB 或留空")
    private String color;

    public void setName(String name) {
        this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
    }
}
