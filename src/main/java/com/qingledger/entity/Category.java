package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qingledger.enums.CategoryType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private CategoryType type;

    private String icon;

    private String color;

    private Integer sortOrder;

    private Boolean isSystem;

    private Long userId;

    private LocalDateTime createdAt;
}
