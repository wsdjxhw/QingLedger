package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {

    @Select("SELECT COUNT(*) FROM `transaction` WHERE category_id = #{categoryId}")
    Long countByCategoryId(@Param("categoryId") Integer categoryId);
}
