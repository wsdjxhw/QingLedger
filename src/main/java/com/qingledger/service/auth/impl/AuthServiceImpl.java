package com.qingledger.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.qingledger.entity.User;
import com.qingledger.entity.UserAuth;
import com.qingledger.common.Result;
import com.qingledger.exception.AuthException;
import com.qingledger.mapper.UserAuthMapper;
import com.qingledger.mapper.UserMapper;
import com.qingledger.service.auth.AuthService;
import com.qingledger.service.auth.TokenService;
import com.qingledger.service.auth.VerificationService;
import com.qingledger.service.user.UserService;
import com.qingledger.service.userauth.UserAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final VerificationService verificationService;
    private final TokenService tokenService;
    private final UserAuthService userAuthService;
    private final UserAuthMapper userAuthMapper;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    // 认证类型常量
    private static final String AUTH_TYPE_USERNAME = "USERNAME";
    private static final String AUTH_TYPE_PHONE = "PHONE";
    private static final String AUTH_TYPE_EMAIL = "EMAIL";

    // 用户状态常量
    private static final Integer USER_STATUS_ACTIVE = 1;
    private static final Integer USER_STATUS_DISABLED = 0;

    // 手机号格式校验正则
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    // 邮箱格式校验正则
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$";

    public AuthServiceImpl(UserService userService,
                          VerificationService verificationService,
                          TokenService tokenService,
                          UserAuthService userAuthService,
                          UserAuthMapper userAuthMapper,
                          UserMapper userMapper,
                          BCryptPasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.verificationService = verificationService;
        this.tokenService = tokenService;
        this.userAuthService = userAuthService;
        this.userAuthMapper = userAuthMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Result<Void> sendCode(String target, String type) {
        log.info("发送验证码: target={}, type={}", target, type);

        // 校验目标格式
        validateTargetFormat(target);

        // 检查用户是否存在（注册时需要检查，登录时也需要检查）
        if ("REGISTER".equalsIgnoreCase(type)) {
            // 注册时，如果用户已存在则报错
            if (existsByPhoneOrEmail(target)) {
                return Result.fail("该手机号或邮箱已被注册");
            }
        } else if ("LOGIN".equalsIgnoreCase(type) || "RESET_PASSWORD".equalsIgnoreCase(type)) {
            // 登录或重置密码时，如果用户不存在则报错
            if (!existsByPhoneOrEmail(target)) {
                return Result.fail("该手机号或邮箱未注册");
            }
        }

        // 根据 target 判断验证码类型（PHONE 或 EMAIL）
        String verificationType;
        if (target.matches(PHONE_PATTERN)) {
            verificationType = AUTH_TYPE_PHONE;
        } else if (target.matches(EMAIL_PATTERN)) {
            verificationType = AUTH_TYPE_EMAIL;
        } else {
            return Result.fail("不支持的验证码目标类型");
        }

        // 发送验证码
        try {
            verificationService.sendCode(verificationType, target);
            return Result.ok();
        } catch (Exception e) {
            log.error("验证码发送失败", e);
            return Result.fail("验证码发送失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<Long> register(String username, String password, String contact, String code) {
        log.info("用户注册: username={}, contact={}", username, contact);

        // 校验联系方式格式
        validateTargetFormat(contact);

        // 校验用户名
        if (username == null || username.trim().isEmpty()) {
            return Result.fail("用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 20) {
            return Result.fail("用户名长度必须在3-20个字符之间");
        }

        // 校验密码
        if (password == null || password.length() < 6 || password.length() > 20) {
            return Result.fail("密码长度必须在6-20个字符之间");
        }

        // 校验验证码
        try {
            // 根据 contact 判断验证码类型（PHONE 或 EMAIL）
            String verificationType;
            if (contact.matches(PHONE_PATTERN)) {
                verificationType = AUTH_TYPE_PHONE;
            } else if (contact.matches(EMAIL_PATTERN)) {
                verificationType = AUTH_TYPE_EMAIL;
            } else {
                return Result.fail("不支持的联系方式类型");
            }

            if (!verificationService.verifyCode(verificationType, contact, code)) {
                return Result.fail("验证码错误或已过期");
            }
        } catch (Exception e) {
            log.error("验证码校验失败", e);
            return Result.fail("验证码校验失败: " + e.getMessage());
        }

        // 检查用户名是否已存在
        if (existsByUsername(username)) {
            return Result.fail("用户名已存在");
        }

        // 检查手机号或邮箱是否已存在
        if (existsByPhoneOrEmail(contact)) {
            return Result.fail("该手机号或邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setNickname(username);
        user.setStatus(USER_STATUS_ACTIVE);

        userMapper.insert(user);

        // 创建用户认证信息
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(user.getId());
        userAuth.setPassword(passwordEncoder.encode(password));
        userAuth.setVerified(true);
        userAuth.setIsPrimary(true);
        userAuth.setBindAt(LocalDateTime.now());

        // 根据联系方式类型设置认证类型和标识
        String authType;
        if (contact.matches(PHONE_PATTERN)) {
            authType = AUTH_TYPE_PHONE;
        } else if (contact.matches(EMAIL_PATTERN)) {
            authType = AUTH_TYPE_EMAIL;
        } else {
            authType = AUTH_TYPE_USERNAME;
        }

        userAuth.setAuthType(authType);
        userAuth.setIdentifier(contact);

        userAuthMapper.insert(userAuth);

        log.info("用户注册成功: userId={}", user.getId());
        return Result.ok(user.getId());
    }

    @Override
    public Result<Long> login(String account, String password) {
        log.info("用户登录: account={}", account);

        if (account == null || account.trim().isEmpty()) {
            return Result.fail("账号不能为空");
        }
        if (password == null || password.isEmpty()) {
            return Result.fail("密码不能为空");
        }

        // 查找用户认证信息
        UserAuth userAuth = findUserAuthByAccount(account);
        log.debug("查询结果: userAuth={}", userAuth);
        if (userAuth == null) {
            log.warn("用户认证信息未找到: account={}", account);
            return Result.fail("账号或密码错误");
        }

        // 验证密码
        log.debug("开始验证密码: userId={}", userAuth.getUserId());
        if (!passwordEncoder.matches(password, userAuth.getPassword())) {
            log.warn("密码验证失败: account={}", account);
            return Result.fail("账号或密码错误");
        }
        log.debug("密码验证成功");

        // 检查用户状态
        User user = userMapper.selectById(userAuth.getUserId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (!USER_STATUS_ACTIVE.equals(user.getStatus())) {
            return Result.fail("用户已被禁用");
        }

        log.info("用户登录成功: userId={}", userAuth.getUserId());
        return Result.ok(userAuth.getUserId());
    }

    @Override
    public Result<Long> loginWithCode(String target, String code) {
        log.info("验证码登录: target={}", target);

        // 校验目标格式
        validateTargetFormat(target);

        // 校验验证码
        try {
            // 根据 target 判断验证码类型（PHONE 或 EMAIL）
            String verificationType;
            if (target.matches(PHONE_PATTERN)) {
                verificationType = AUTH_TYPE_PHONE;
            } else if (target.matches(EMAIL_PATTERN)) {
                verificationType = AUTH_TYPE_EMAIL;
            } else {
                return Result.fail("不支持的验证码目标类型");
            }

            if (!verificationService.verifyCode(verificationType, target, code)) {
                return Result.fail("验证码错误或已过期");
            }
        } catch (Exception e) {
            log.error("验证码校验失败", e);
            return Result.fail("验证码校验失败: " + e.getMessage());
        }

        // 查找用户
        UserAuth userAuth = findUserAuthByPhoneOrEmail(target);
        if (userAuth == null) {
            return Result.fail("该手机号或邮箱未注册");
        }

        // 检查用户状态
        User user = userMapper.selectById(userAuth.getUserId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (!USER_STATUS_ACTIVE.equals(user.getStatus())) {
            return Result.fail("用户已被禁用");
        }

        log.info("验证码登录成功: userId={}", userAuth.getUserId());
        return Result.ok(userAuth.getUserId());
    }

    @Override
    @Transactional
    public Result<Void> resetPassword(String target, String newPassword, String code) {
        log.info("重置密码: target={}", target);

        // 校验目标格式
        validateTargetFormat(target);

        // 校验新密码
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            return Result.fail("密码长度必须在6-20个字符之间");
        }

        // 校验验证码
        try {
            // 根据 target 判断验证码类型（PHONE 或 EMAIL）
            String verificationType;
            if (target.matches(PHONE_PATTERN)) {
                verificationType = AUTH_TYPE_PHONE;
            } else if (target.matches(EMAIL_PATTERN)) {
                verificationType = AUTH_TYPE_EMAIL;
            } else {
                return Result.fail("不支持的验证码目标类型");
            }

            if (!verificationService.verifyCode(verificationType, target, code)) {
                return Result.fail("验证码错误或已过期");
            }
        } catch (Exception e) {
            log.error("验证码校验失败", e);
            return Result.fail("验证码校验失败: " + e.getMessage());
        }

        // 查找用户认证信息
        UserAuth userAuth = findUserAuthByPhoneOrEmail(target);
        if (userAuth == null) {
            return Result.fail("该手机号或邮箱未注册");
        }

        // 更新密码
        userAuth.setPassword(passwordEncoder.encode(newPassword));
        userAuthMapper.updateById(userAuth);

        log.info("密码重置成功: userId={}", userAuth.getUserId());
        return Result.ok();
    }

    @Override
    public Result<String> refreshToken(String refreshToken) {
        log.info("刷新令牌");

        try {
            TokenService.TokenPairResult tokens = tokenService.refreshAccessToken(refreshToken);
            return Result.ok(tokens.accessToken());
        } catch (Exception e) {
            log.error("刷新令牌失败", e);
            return Result.fail("刷新令牌失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<Void> bindEmail(Long userId, String email, String code) {
        log.info("绑定邮箱请求: userId={}, Email={}", userId, email);
        //校验验证码
        try{
            if(!verificationService.verifyCode(AUTH_TYPE_EMAIL,email,code)){
                return Result.fail("验证码错误或已失效");
            }
        }catch(Exception e){
            log.error("验证码校验失败",e);
            return Result.fail("验证码校验失败: " + e.getMessage());
        }
        UserAuth userAuth = findUserAuthByPhoneOrEmail(email);
        if (userAuth != null) {
            return Result.fail("该邮箱已被绑定");
        }

        List<UserAuth> userAuths = userAuthMapper.selectList(new QueryWrapper<UserAuth>().eq("user_id", userId));
        String password = null;
        for(UserAuth UA : userAuths){
            if(UA.getAuthType().equals(AUTH_TYPE_EMAIL)){
                return Result.fail("当前账号已经绑定邮箱,请勿重复绑定");
            }
            if(UA.getPassword() != null){
                password = UA.getPassword();
            }
        }

        UserAuth newUserAuth = new UserAuth();
        newUserAuth.setUserId(userId);
        newUserAuth.setAuthType(AUTH_TYPE_EMAIL);
        newUserAuth.setIdentifier(email);
        newUserAuth.setPassword(password);
        newUserAuth.setVerified(true);
        newUserAuth.setIsPrimary(false);
        newUserAuth.setBindAt(LocalDateTime.now());

        userAuthMapper.insert(newUserAuth);

        log.info("邮箱绑定成功: userId={}, email={}", userId, email);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> bindPhone(Long userId, String phone, String code) {
        log.info("绑定手机号请求: userId={}, phone={}", userId, phone);

        // 参考邮箱绑定的实现步骤:
        // 1. 校验验证码
        try{
            if(!verificationService.verifyCode(AUTH_TYPE_PHONE,phone,code)){
                return Result.fail("验证码错误或已失效");
            }
        }catch(Exception e){
            log.error("验证码校验失败",e);
            return Result.fail("验证码校验失败: " + e.getMessage());
        }
        // 2. 检查手机号是否已被绑定
        UserAuth userAuth = findUserAuthByPhoneOrEmail(phone);
        if(userAuth != null){
            return Result.fail("该手机号已被绑定");
        }
        // 3. 检查当前账号是否已绑定手机号
        List<UserAuth> userAuths = userAuthMapper.selectList(new QueryWrapper<UserAuth>().eq("user_id", userId));
        String password = null;
        for(UserAuth UA : userAuths){
            if(UA.getAuthType().equals(AUTH_TYPE_PHONE)){
                return Result.fail("当前账号已经绑定手机号,请勿重复绑定");
            }
            // 4. 获取现有密码（密码继承逻辑）
            if(UA.getPassword() != null){
                password = UA.getPassword();
            }
        }
        // 5. 创建新的 UserAuth 记录
        UserAuth newUserAuth = new UserAuth();
        newUserAuth.setUserId(userId);
        newUserAuth.setAuthType(AUTH_TYPE_PHONE);
        newUserAuth.setIdentifier(phone);
        newUserAuth.setPassword(password);
        newUserAuth.setVerified(true);
        newUserAuth.setIsPrimary(false);
        newUserAuth.setBindAt(LocalDateTime.now());

        userAuthMapper.insert(newUserAuth);
        // 6. 记录日志并返回成功
        log.info("手机号绑定成功: userId={}, phone={}", userId, phone);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> unbind(Long userId, String authType, String password) {
        log.info("解绑登录方式请求: userId={}, authType={}", userId, authType);

        // 1. 参数校验：authType 必须是 PHONE 或 EMAIL
        if (!AUTH_TYPE_PHONE.equals(authType) && !AUTH_TYPE_EMAIL.equals(authType)) {
            return Result.fail("认证类型必须是 PHONE 或 EMAIL");
        }

        // 2. 查询该用户所有 UserAuth 记录
        List<UserAuth> userAuths = userAuthMapper.selectList(
                new QueryWrapper<UserAuth>().eq("user_id", userId));

        // 3. 检查最后一条保护：用户绑定数 <= 1 时拒绝解绑
        if (userAuths.size() <= 1) {
            return Result.fail("至少保留一种登录方式,请先绑定新的登录方式");
        }

        // 4. 查找待解绑的目标记录
        UserAuth targetAuth = null;
        for (UserAuth ua : userAuths) {
            if (authType.equals(ua.getAuthType())) {
                targetAuth = ua;
                break;
            }
        }
        if (targetAuth == null) {
            return Result.fail("未找到该认证方式");
        }

        // 5. 密码校验
        if (!passwordEncoder.matches(password, targetAuth.getPassword())) {
            log.warn("解绑密码验证失败: userId={}, authType={}", userId, authType);
            return Result.fail("密码错误");
        }

        // 6. 处理主账号：若待删除记录是主账号,则将剩余的第一条设为主账号
        if (Boolean.TRUE.equals(targetAuth.getIsPrimary())) {
            for (UserAuth ua : userAuths) {
                if (!ua.getId().equals(targetAuth.getId())) {
                    ua.setIsPrimary(true);
                    userAuthMapper.updateById(ua);
                    log.info("主账号已转移: userId={}, newPrimaryAuthType={}", userId, ua.getAuthType());
                    break;
                }
            }
        }

        // 7. 执行删除
        userAuthMapper.deleteById(targetAuth.getId());

        log.info("解绑成功: userId={}, authType={}", userId, authType);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> switchPrimary(Long userId, String authType) {
        log.info("切换主账号请求: userId={}, authType={}", userId, authType);

        // 防御式校验：仅允许 PHONE / EMAIL
        if (!AUTH_TYPE_PHONE.equals(authType) && !AUTH_TYPE_EMAIL.equals(authType)) {
            return Result.fail("不支持的登录方式，仅支持 PHONE 和 EMAIL");
        }

        List<UserAuth> userAuths = userAuthMapper.selectByUserId(userId);
        if (userAuths == null || userAuths.isEmpty()) {
            return Result.fail("该登录方式尚未绑定，无法设为主账号");
        }

        UserAuth targetAuth = null;
        String oldPrimaryType = null;
        for (UserAuth auth : userAuths) {
            if (Boolean.TRUE.equals(auth.getIsPrimary())) {
                oldPrimaryType = auth.getAuthType();
            }
            if (authType.equals(auth.getAuthType())) {
                targetAuth = auth;
            }
        }

        if (targetAuth == null) {
            return Result.fail("该登录方式尚未绑定，无法设为主账号");
        }

        if (Boolean.TRUE.equals(targetAuth.getIsPrimary())) {
            return Result.ok("已是主账号，无需切换", null);
        }

        int affected = userAuthMapper.setPrimaryByUserIdAndAuthType(userId, authType);
        if (affected <= 0) {
            return Result.fail("主账号切换失败");
        }

        log.info("主账号切换成功: userId={}, oldPrimary={}, newPrimary={}", userId, oldPrimaryType, authType);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> changePassword(Long userId, String oldPassword, String newPassword) {
        log.info("修改密码请求: userId={}", userId);

        // 1. 校验新旧密码不相同(在 DB 查询之前拦截)
        if (oldPassword != null && oldPassword.equals(newPassword)) {
            return Result.fail("新密码不能与旧密码相同");
        }

        // 2. 查询该用户所有 UserAuth 记录
        List<UserAuth> userAuths = userAuthMapper.selectList(
                new QueryWrapper<UserAuth>().eq("user_id", userId));

        // 3. 用户不存在(无任何 UserAuth)
        if (userAuths.isEmpty()) {
            return Result.fail("用户不存在");
        }

        // 4. 用第一条记录的 password 做 BCrypt 校验旧密码
        //    (该用户所有 UserAuth.password 已通过本接口同步,任意一条均可,固定取第一条以消除歧义)
        UserAuth firstAuth = userAuths.get(0);
        if (!passwordEncoder.matches(oldPassword, firstAuth.getPassword())) {
            log.warn("修改密码-旧密码错误: userId={}", userId);
            return Result.fail("旧密码错误");
        }

        // 5. 单 SQL 条件更新该用户全部 UserAuth.password,避免逐条 update 造成部分成功
        String encoded = passwordEncoder.encode(newPassword);
        userAuthMapper.update(null,
                new UpdateWrapper<UserAuth>().set("password", encoded).eq("user_id", userId));

        log.info("修改密码成功: userId={}", userId);
        return Result.ok();
    }

    /**
     * 校验目标格式（手机号或邮箱）
     */
    private void validateTargetFormat(String target) {
        if (target == null || target.trim().isEmpty()) {
            throw new AuthException(1001, "目标不能为空");
        }

        boolean isPhone = target.matches(PHONE_PATTERN);
        boolean isEmail = target.matches(EMAIL_PATTERN);

        if (!isPhone && !isEmail) {
            throw new AuthException(1002, "请输入有效的手机号或邮箱");
        }
    }

    /**
     * 检查用户名是否存在
     */
    private boolean existsByUsername(String username) {
        QueryWrapper<UserAuth> wrapper = new QueryWrapper<>();
        wrapper.eq("auth_type", AUTH_TYPE_USERNAME)
               .eq("identifier", username);
        return userAuthMapper.selectCount(wrapper) > 0;
    }

    /**
     * 检查手机号或邮箱是否存在
     */
    private boolean existsByPhoneOrEmail(String phoneOrEmail) {
        QueryWrapper<UserAuth> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("auth_type", AUTH_TYPE_PHONE)
                          .eq("identifier", phoneOrEmail)
                          .or()
                          .eq("auth_type", AUTH_TYPE_EMAIL)
                          .eq("identifier", phoneOrEmail));
        return userAuthMapper.selectCount(wrapper) > 0;
    }

    /**
     * 通过账号查找用户认证信息
     */
    private UserAuth findUserAuthByAccount(String account) {
        QueryWrapper<UserAuth> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                .and(w1 -> w1.eq("auth_type", AUTH_TYPE_USERNAME).eq("identifier", account))
                .or(w2 -> w2.eq("auth_type", AUTH_TYPE_PHONE).eq("identifier", account))
                .or(w3 -> w3.eq("auth_type", AUTH_TYPE_EMAIL).eq("identifier", account))
        );
        List<UserAuth> results = userAuthMapper.selectList(wrapper);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 通过手机号或邮箱查找用户认证信息
     */
    private UserAuth findUserAuthByPhoneOrEmail(String phoneOrEmail) {
        QueryWrapper<UserAuth> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("auth_type", AUTH_TYPE_PHONE)
                          .eq("identifier", phoneOrEmail)
                          .or()
                          .eq("auth_type", AUTH_TYPE_EMAIL)
                          .eq("identifier", phoneOrEmail));
        return userAuthMapper.selectOne(wrapper);
    }
}
