package com.qingledger.dto.request;

import com.qingledger.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTransactionRequest {

    @NotNull(message = "账本ID不能为空")
    private Long ledgerId;

    @NotNull(message = "type 不能为空")
    private TransactionType type;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    private Integer categoryId;

    private String tags;

    @NotNull(message = "occurDate 不能为空")
    private String occurDate;

    private String note;
}
