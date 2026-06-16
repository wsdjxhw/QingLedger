package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.qingledger.enums.MemberRole;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ledger_member")
public class LedgerMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ledgerId;

    private Long userId;

    private MemberRole role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;
}
