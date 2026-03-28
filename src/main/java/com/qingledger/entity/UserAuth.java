package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户认证方式实体
 */
@Data
@TableName("user_auth")
public class UserAuth {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String authType;

    private String identifier;

    private String password;

    private Boolean verified;

    private Boolean isPrimary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime bindAt;
}
