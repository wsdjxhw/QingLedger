package com.qingledger.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TransactionMapper {

    @Select("SELECT COUNT(*) FROM `transaction` WHERE category_id = #{categoryId}")
    Long countByCategoryId(@Param("categoryId") Integer categoryId);
}
