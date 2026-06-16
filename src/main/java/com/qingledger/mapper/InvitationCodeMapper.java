package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.InvitationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InvitationCodeMapper extends BaseMapper<InvitationCode> {

    //原子性做完校验和消费
    @Update("UPDATE invitation_code SET use_count = use_count + 1 " +
            "WHERE code = #{code} AND status = 'active' " +
            "AND (expires_at IS NULL OR expires_at > NOW()) " +
            "AND use_count < max_uses")
    int consumeCode(@Param("code") String code);
}
