package com.qingledger.dto.request;

import com.qingledger.enums.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionListQuery {

    private TransactionType type;

    private Integer categoryId;

    private String tag;

    private String startDate;

    private String endDate;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    @Min(value = 1, message = "page 必须 >= 1")
    private int page = 1;

    @Min(value = 1, message = "size 必须 >= 1")
    @Max(value = 100, message = "size 必须 <= 100")
    private int size = 20;

    private String sortBy = "occur_date";

    private String sortOrder = "desc";
}
