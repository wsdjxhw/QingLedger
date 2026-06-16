package com.qingledger.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;
    private int page;
    private int size;
    private long total;
    private TransactionSummary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionSummary {
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
    }
}
