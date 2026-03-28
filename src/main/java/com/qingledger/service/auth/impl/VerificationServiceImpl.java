package com.qingledger.service.auth.impl;

import com.qingledger.exception.VerificationException;
import com.qingledger.service.auth.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class VerificationServiceImpl implements VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;
    private static final Duration CODE_EXPIRE = Duration.ofMinutes(5);
    private static final Duration MINUTE_LIMIT = Duration.ofMinutes(1);
    private static final Duration DAY_LIMIT = Duration.ofDays(1);
    private static final int MAX_DAILY_COUNT = 10;

    private final RedisTemplate<String, Object> redisTemplate;

    public VerificationServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void sendCode(String type, String target) {
        // 检查1分钟限制
        String minuteKey = getMinuteLimitKey(target);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(minuteKey))) {
            throw new VerificationException(1007, "验证码发送过于频繁,请1分钟后再试");
        }

        // 检查1天限制
        checkDailyLimit(target);

        // 生成验证码
        String code = generateCode();

        // 存储验证码
        String codeKey = getCodeKey(type, target);
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE);

        // 设置1分钟限制
        redisTemplate.opsForValue().set(minuteKey, "1", MINUTE_LIMIT);

        // 更新日计数
        String limitKey = getLimitKey(target);
        redisTemplate.opsForValue().increment(limitKey);
        redisTemplate.expire(limitKey, DAY_LIMIT);

        // 开发环境: 控制台输出
        log.info("========== 验证码 ==========");
        log.info("类型: {}", type);
        log.info("目标: {}", target);
        log.info("验证码: {}", code);
        log.info("过期时间: {} 分钟", CODE_EXPIRE.toMinutes());
        log.info("============================");
    }

    @Override
    public boolean verifyCode(String type, String target, String code) {
        String codeKey = getCodeKey(type, target);
        String storedCode = (String) redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            throw new VerificationException(1006, "验证码已过期");
        }

        if (!storedCode.equals(code)) {
            // 增加失败计数
            String failKey = getFailKey(type, target);
            Long failCount = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, CODE_EXPIRE);

            if (failCount >= 5) {
                deleteCode(type, target);
                throw new VerificationException(1015, "验证码验证失败次数过多");
            }
            throw new VerificationException(1005, "验证码错误");
        }

        // 验证成功,删除验证码
        deleteCode(type, target);
        return true;
    }

    @Override
    public void deleteCode(String type, String target) {
        String codeKey = getCodeKey(type, target);
        redisTemplate.delete(codeKey);
    }

    private void checkDailyLimit(String target) {
        String limitKey = getLimitKey(target);
        Long count = (Long) redisTemplate.opsForValue().get(limitKey);
        if (count != null && count >= MAX_DAILY_COUNT) {
            throw new VerificationException(1007, "今日验证码发送次数已达上限");
        }
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    private String getCodeKey(String type, String target) {
        return "verification:" + type + ":" + target;
    }

    private String getLimitKey(String target) {
        return "verification_limit:" + target;
    }

    private String getMinuteLimitKey(String target) {
        return "verification_limit_minute:" + target;
    }

    private String getFailKey(String type, String target) {
        return "verification_fail:" + type + ":" + target;
    }
}
