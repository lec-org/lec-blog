package org.lec.blog.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求参数无效"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "缺少有效的认证令牌"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有访问权限"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "博客不存在"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "请在上一次手动同步的 5 分钟后重试"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
