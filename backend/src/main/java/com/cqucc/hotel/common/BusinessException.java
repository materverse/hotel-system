package com.cqucc.hotel.common;

/**
 * 业务异常。业务校验失败统一抛出该异常，由全局异常处理器转换为标准响应。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
