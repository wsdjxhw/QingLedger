-- 验证设备信息
-- KEYS[1]: RefreshTokenInfo key (refresh_token_info:{userId}:{tokenId})
-- ARGV[1]: 期望的设备指纹
-- ARGV[2]: 期望的城市
-- 返回: 1=验证通过, 0=Token不存在, -1=设备指纹不匹配

local info = redis.call('get', KEYS[1])
if not info then
    return 0
end

-- 设备验证在 Java 端处理 JSON 解析和比对
-- 这里只负责检查 Token 是否存在
return 1
