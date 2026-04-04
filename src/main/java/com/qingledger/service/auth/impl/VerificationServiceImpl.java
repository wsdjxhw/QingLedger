package com.qingledger.service.auth.impl;

import com.qingledger.exception.VerificationException;
import com.qingledger.service.auth.VerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

@Slf4j
@Service
public class VerificationServiceImpl implements VerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;
    private static final Duration CODE_EXPIRE = Duration.ofMinutes(1);
    private static final Duration MINUTE_LIMIT = Duration.ofSeconds(65);
    //private static final int MAX_DAILY_COUNT = 15;

    private static final DefaultRedisScript<Long> SEND_CODE_REDIS_SCRIPT;

    static {
        ClassPathResource resource = new ClassPathResource("lua/verification/send_code.lua");
        String scriptText;
        try {
            scriptText = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script", e);
        }

        SEND_CODE_REDIS_SCRIPT = new DefaultRedisScript<>();
        SEND_CODE_REDIS_SCRIPT.setScriptText(scriptText);
        SEND_CODE_REDIS_SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, Object> redisTemplate;

    public VerificationServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void sendCode(String type, String target) {
        // 1. 生成验证码
        String code = generateCode();

        // 2. 准备Redis keys
        String codeKey = getCodeKey(type, target);
        String limitKey = getDailyLimitKey(target);
        String minuteKey = getMinuteLimitKey(target);
        long secondsUntilEndOfDay = calculateSecondsUntilEndOfDay();

        // 3. 用一个Lua脚本原子性地完成所有操作
        // 包括：检查1分钟限制、检查日限制、存储验证码、设置1分钟限制、更新日计数
        Long result = redisTemplate.execute(
                SEND_CODE_REDIS_SCRIPT,
                Arrays.asList(codeKey, limitKey, minuteKey),
                code,                                           // ARGV[1]: 验证码值
                String.valueOf(CODE_EXPIRE.getSeconds()),      // ARGV[2]: 验证码过期秒数
                String.valueOf(secondsUntilEndOfDay),          // ARGV[3]: 日计数过期秒数
                String.valueOf(MINUTE_LIMIT.getSeconds())       // ARGV[4]: 1分钟限制秒数
        );

        // 4. 处理Lua脚本返回的错误码
        if (result == -1) {
            throw new VerificationException(1001, "验证码发送过于频繁,请稍后再试");
        } else if (result == -2) {
            throw new VerificationException(1002, "今日验证码发送次数已达15次上限");
        }

        // 5. 开发环境: 控制台输出
        log.info("========== 验证码 ==========");
        log.info("类型: {}", type);
        log.info("目标: {}", target);
        log.info("验证码: {}", code);
        log.info("过期时间: {} 秒", CODE_EXPIRE.getSeconds());
        log.info("============================");
    }

    @Override
    public boolean verifyCode(String type, String target, String code) {
        String codeKey = getCodeKey(type, target);
        String storedCode = (String) redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            throw new VerificationException(1006, "验证码已失效,请等待冷却时间结束后再次发送");
        }

        if (!storedCode.equals(code)) {
            // 增加失败计数
            String failKey = getFailKey(type, target);
            Long failCount = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, CODE_EXPIRE);

            if (failCount >= 5) {
                deleteCode(type, target);
                throw new VerificationException(1015, "验证码验证失败次数过多,请等待冷却时间结束后重新发送验证码");
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

    private String getDailyLimitKey(String target) {
        return "verification_limit:" + target + ":" + java.time.LocalDate.now();
    }

    private String getMinuteLimitKey(String target) {
        return "verification_limit_minute:" + target;
    }

    private String getFailKey(String type, String target) {
        return "verification_fail:" + type + ":" + target;
    }

    private long calculateSecondsUntilEndOfDay() {
        LocalDateTime endOfToday = LocalDateTime.now().with(LocalTime.MAX);
        return ChronoUnit.SECONDS.between(LocalDateTime.now(), endOfToday);
    }
}
