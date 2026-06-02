package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("invitation_code")
public class InvitationCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long ledgerId;

    private Long creatorId;

    private Integer maxUses;

    private Integer useCount;

    private LocalDateTime expiresAt;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
