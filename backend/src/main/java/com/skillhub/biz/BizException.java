package com.skillhub.biz;

/**
 * 业务层统一异常。
 * Controller 层捕获后转换为 400 响应。
 */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}