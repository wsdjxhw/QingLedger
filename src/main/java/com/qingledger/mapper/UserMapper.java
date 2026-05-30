package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层 - User 表的 Mapper 接口
 *
 * 功能说明：
 * - 提供 User 实体的 CRUD 操作
 * - 继承 MyBatis-Plus 的 BaseMapper，自动拥有基础方法
 *
 * BaseMapper 提供的方法：
 * - insert: 插入单条记录
 * - deleteById: 根据ID删除
 * - updateById: 根据ID更新
 * - selectById: 根据ID查询
 * - selectList: 条件查询列表
 * - selectPage: 分页查询
 * - 等等...
 *
 * 使用示例：
 * <pre>
 * // 查询用户
 * User user = userMapper.selectById(1L);
 *
 * // 插入用户
 * userMapper.insert(user);
 *
 * // 条件查询
 * userMapper.selectList(new QueryWrapper<User>().eq("status", 0));
 * </pre>
 *
 * @author QingLedger Team
 * @since 2026-04-04 完成回顾和注释
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper 后，自动拥有所有基础 CRUD 方法
    // 如需自定义 SQL，可以在这里添加方法并配合 XML 文件实现
}
