package com.qingledger.dto.request;

import com.qingledger.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 30, message = "分类名称长度不能超过30个字符")
    private String name;

    @NotNull(message = "type 不能为空")
    private CategoryType type;

    @Size(max = 50, message = "图标标识长度不能超过50个字符")
    private String icon;

    @Pattern(regexp = "^#[A-Fa-f0-9]{6}$", message = "颜色格式不正确，请使用 #RRGGBB")
    private String color;

    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
    }
}
