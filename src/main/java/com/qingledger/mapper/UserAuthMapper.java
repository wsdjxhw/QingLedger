package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.UserAuth;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户认证方式数据访问层 - UserAuth 表的 Mapper 接口
 *
 * 功能说明：
 * - 提供 UserAuth 实体的 CRUD 操作
 * - 继承 MyBatis-Plus 的 BaseMapper，自动拥有基础方法
 *
 * BaseMapper 提供的方法：
 * - insert: 插入单条记录
 * - deleteById: 根据ID删除
 * - updateById: 根据ID更新
 * - selectById: 根据ID查询
 * - selectList: 条件查询列表
 * - selectOne: 根据条件查询单条记录
 * - 等等...
 *
 * 常用查询场景：
 * - 根据手机号/邮箱查询用户登录信息
 * - 查询用户的所有绑定方式
 * - 查询主账号信息
 *
 * 使用示例：
 * <pre>
 * // 根据手机号查询
 * UserAuth auth = userAuthMapper.selectOne(
 *     new QueryWrapper<UserAuth>()
 *         .eq("auth_type", "PHONE")
 *         .eq("identifier", "13800138000")
 * );
 *
 * // 查询用户的所有绑定方式
 * userAuthMapper.selectList(
 *     new QueryWrapper<UserAuth>().eq("user_id", userId)
 * );
 * </pre>
 *
 * @author QingLedger Team
 * @since 2026-04-04 完成回顾和注释
 */
@Mapper
public interface UserAuthMapper extends BaseMapper<UserAuth> {
    // 继承 BaseMapper 后，自动拥有所有基础 CRUD 方法
    // 如需自定义 SQL，可以在这里添加方法并配合 XML 文件实现

    @Select("SELECT * FROM user_auth WHERE user_id = #{userId}")
    List<UserAuth> selectByUserId(@Param("userId") Long userId);

    @Update({
            "UPDATE user_auth",
            "SET is_primary = CASE WHEN auth_type = #{authType} THEN TRUE ELSE FALSE END",
            "WHERE user_id = #{userId}",
            "AND auth_type IN ('PHONE', 'EMAIL')"
    })
    int setPrimaryByUserIdAndAuthType(@Param("userId") Long userId, @Param("authType") String authType);
}
