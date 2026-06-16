package com.qingledger.dto.request;

import com.qingledger.enums.TransactionType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateTransactionRequest {

    private TransactionType type;

    private BigDecimal amount;

    private Integer categoryId;

    private String tags;

    private String occurDate;

    private String note;
}
