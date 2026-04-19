-- 存储 RefreshToken 及其设备信息
-- KEYS[1]: RefreshToken key (refresh_token:{userId}:{tokenId})
-- KEYS[2]: RefreshTokenInfo key (refresh_token_info:{userId}:{tokenId})
-- ARGV[1]: RefreshToken 值
-- ARGV[2]: 过期时间(秒)
-- ARGV[3]: RefreshTokenInfo JSON（由 GenericJackson2JsonRedisSerializer 自动序列化）
-- 返回: 1=成功

redis.call('setex', KEYS[1], ARGV[2], ARGV[1])
redis.call('setex', KEYS[2], ARGV[2], ARGV[3])

return 1

redis.call('setex', KEYS[1], ARGV[2], ARGV[1])
redis.call('setex', KEYS[2], ARGV[2], ARGV[3])

return 1
