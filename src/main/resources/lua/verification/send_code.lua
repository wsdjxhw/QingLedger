-- 验证码发送完整流程（原子操作）
-- KEYS[1]: 验证码key (例如: verification:REGISTER:13800138000)
-- KEYS[2]: 日计数key (例如: verification_limit:13800138000:2026-04-04)
-- KEYS[3]: 1分钟限制key (例如: verification_limit_minute:13800138000)
-- ARGV[1]: 验证码值
-- ARGV[2]: 验证码过期秒数
-- ARGV[3]: 日计数过期秒数（到今天23:59:59的秒数）
-- ARGV[4]: 1分钟限制秒数
-- 返回: -1=1分钟限制, -2=日限制(15次), 其他=日计数值

-- 1. 检查1分钟限制
if redis.call('exists', KEYS[3]) == 1 then
    return -1
end

-- 2. 检查日计数
local dailyCount = redis.call('get', KEYS[2])
if dailyCount and tonumber(dailyCount) >= 15 then
    return -2
end

-- 3. 存储验证码（带过期时间）
redis.call('setex', KEYS[1], ARGV[2], ARGV[1])

-- 4. 设置1分钟限制
redis.call('setex', KEYS[3], ARGV[4], '1')

-- 5. 更新日计数
dailyCount = redis.call('incr', KEYS[2])
if dailyCount == 1 then
    -- 只有第一次设置时才设置过期时间到今天23:59:59
    redis.call('expire', KEYS[2], ARGV[3])
end

return dailyCount
