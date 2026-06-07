package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {

    @Select("SELECT COUNT(*) FROM `transaction` WHERE category_id = #{categoryId}")
    Long countByCategoryId(@Param("categoryId") Integer categoryId);

    @Select("SELECT COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) FROM `transaction` WHERE ledger_id = #{ledgerId}")
    BigDecimal sumIncomeByLedger(@Param("ledgerId") Long ledgerId);

    @Select("SELECT COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) FROM `transaction` WHERE ledger_id = #{ledgerId}")
    BigDecimal sumExpenseByLedger(@Param("ledgerId") Long ledgerId);
}
