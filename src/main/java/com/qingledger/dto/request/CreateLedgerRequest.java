package com.qingledger.dto.request;

import com.qingledger.enums.LedgerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateLedgerRequest {

    @NotBlank(message = "账本名称不能为空")
    @Size(max = 50, message = "账本名称长度不能超过50个字符")
    private String name;

    @Size(max = 200, message = "账本描述长度不能超过200个字符")
    private String description;

    @NotNull(message = "账本类型不能为空")
    private LedgerType type;

    @Size(max = 50, message = "图标标识长度不能超过50个字符")
    private String icon;

    @Pattern(regexp = "^#[A-Fa-f0-9]{6}$", message = "颜色格式不正确，请使用 #RRGGBB")
    private String color;

    private List<Long> memberIds;

    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
    }
}
