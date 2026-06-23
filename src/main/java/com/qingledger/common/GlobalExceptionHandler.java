package com.qingledger.common;

import com.qingledger.exception.AuthException;
import com.qingledger.exception.VerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AuthException.class)
    public Result<Void> handleAuthException(AuthException e) {
        log.error("认证异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(VerificationException.class)
    public Result<Void> handleVerificationException(VerificationException e) {
        log.error("验证码异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

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

    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        String message = "参数校验失败";
        if (!e.getAllValidationResults().isEmpty()
                && !e.getAllValidationResults().get(0).getResolvableErrors().isEmpty()) {
            message = e.getAllValidationResults().get(0).getResolvableErrors().get(0).getDefaultMessage();
        }
        log.error("方法参数校验异常: {}", message);
        return Result.fail(400, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.error("缺少必填参数: {}", e.getParameterName());
        return Result.fail(400, "缺少必填参数: " + e.getParameterName());
    }

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

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.fail("系统繁忙，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统异常，请联系管理员");
    }
}
