package com.qingledger.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TransactionResponse {
    private Long id;
    private Long ledgerId;
    private Long userId;
    private String type;
    private BigDecimal amount;
    private Integer categoryId;
    private String categoryName;
    private String tags;
    private LocalDate occurDate;
    private String note;
    private LocalDateTime createdAt;
}
