package com.qingledger.common;

import com.qingledger.exception.AuthException;
import com.qingledger.exception.VerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author QingLedger Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthException.class)
    public Result<Void> handleAuthException(AuthException e) {
        log.error("认证异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理验证码异常
     */
    @ExceptionHandler(VerificationException.class)
    public Result<Void> handleVerificationException(VerificationException e) {
        log.error("验证码异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidationException(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException validException
                && validException.getBindingResult().getFieldError() != null) {
            message = validException.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException bindException
                && bindException.getFieldError() != null) {
            message = bindException.getFieldError().getDefaultMessage();
        }
        log.error("参数校验异常: {}", message);
        return Result.fail(400, message);
    }

    /**
     * 处理 JSON 反序列化异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof BusinessException be) {
                return Result.fail(be.getCode(), be.getMessage());
            }
            current = current.getCause();
        }
        log.error("请求参数格式错误", e);
        return Result.fail(400, "请求参数格式错误");
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.fail("系统繁忙,请稍后重试");
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统异常,请联系管理员");
    }
}