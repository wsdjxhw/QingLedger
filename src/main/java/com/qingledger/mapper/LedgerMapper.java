package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.Ledger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LedgerMapper extends BaseMapper<Ledger> {
}
