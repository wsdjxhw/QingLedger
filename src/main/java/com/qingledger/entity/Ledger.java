package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.qingledger.enums.LedgerType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ledger")
public class Ledger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    private LedgerType type;

    private String icon;

    private String color;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
