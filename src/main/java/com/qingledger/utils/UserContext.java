package com.qingledger.utils;

import com.qingledger.entity.User;

/**
 * 用户上下文工具类
 *
 * 功能说明：
 * - 使用 ThreadLocal 存储当前请求的用户信息
 * - 在 JWT 过滤器中设置用户信息
 * - 在业务代码中获取当前登录用户
 * - 请求结束后自动清理，防止内存泄漏
 *
 * 使用场景：
 * - Controller 层：获取当前登录用户ID
 * - Service 层：获取当前登录用户信息（不需要层层传递参数）
 * - 任何业务代码：快速访问当前用户上下文
 *
 * 使用示例：
 * <pre>
 * // Controller 中使用
 * @GetMapping("/user/bindings")
 * public Result<List<UserAuth>> getBindings() {
 *     Long userId = UserContext.getUserId();  // 获取当前用户ID
 *     return Result.ok(userAuthService.getUserAuths(userId));
 * }
 *
 * // Service 中使用
 * public void someMethod() {
 *     Long userId = UserContext.getUserId();  // 直接获取，不需要参数传递
 *     User user = UserContext.getUser();      // 获取完整用户信息
 * }
 * </pre>
 *
 * 注意事项：
 * - 只能在请求线程中调用（由 JwtAuthenticationFilter 设置）
 * - 请求结束后会自动清理（由 JwtAuthenticationFilter 调用 clear()）
 * - 不要在异步线程、定时任务等场景中使用（这些场景没有请求上下文）
 *
 * @author QingLedger Team
 * @since 2026-05-04
 */
public class UserContext {

    /**
     * 存储当前用户ID的 ThreadLocal
     */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /**
     * 存储当前用户完整信息的 ThreadLocal
     */
    private static final ThreadLocal<User> USER = new ThreadLocal<>();

    /**
     * 设置当前用户ID
     *
     * 由 JwtAuthenticationFilter 在请求开始时调用
     *
     * @param userId 用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 设置当前用户完整信息
     *
     * 由 JwtAuthenticationFilter 在请求开始时调用
     * 会同时设置 userId（从 user 对象中提取）
     *
     * @param user 用户对象
     */
    public static void setUser(User user) {
        if (user != null) {
            USER.set(user);
            USER_ID.set(user.getId());
        }
    }

    /**
     * 获取当前用户ID
     *
     * 使用场景：
     * - 只需要用户ID进行查询或操作
     * - 性能敏感场景（不需要查询完整用户信息）
     *
     * @return 当前用户ID，如果未认证返回 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取当前用户完整信息
     *
     * 使用场景：
     * - 需要用户的多个属性（昵称、头像等）
     * - 需要判断用户状态
     *
     * @return 当前用户对象，如果未认证返回 null
     */
    public static User getUser() {
        return USER.get();
    }

    /**
     * 清除当前用户信息
     *
     * 重要：请求结束后必须调用，防止：
     * - 内存泄漏（ThreadLocal 持有对象引用）
     * - 用户信息混乱（线程复用时上下文错误）
     *
     * 由 JwtAuthenticationFilter 在请求结束时自动调用
     */
    public static void clear() {
        USER_ID.remove();
        USER.remove();
    }

    /**
     * 检查当前是否有用户登录
     *
     * @return true-已登录, false-未登录
     */
    public static boolean isAuthenticated() {
        return USER_ID.get() != null;
    }
}
