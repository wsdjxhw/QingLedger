package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qingledger.enums.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("transaction")
public class Transaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ledgerId;

    private Long userId;

    private TransactionType type;

    private BigDecimal amount;

    private Integer categoryId;

    private String tags;

    private LocalDate occurDate;

    private String note;

    private String images;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
