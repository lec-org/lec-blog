package org.lec.blog.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.lec.blog.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(basePackages = "org.lec.blog")
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    ResponseEntity<ApiResponse<Void>> business(BizException exception) {
        var status = exception.errorCode().status();
        var response = ResponseEntity.status(status);
        if (exception.errorCode() == ErrorCode.UNAUTHORIZED) {
            response.header("WWW-Authenticate", "Bearer");
        }
        return response.body(ApiResponse.error(status.value(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalidBody(MethodArgumentNotValidException exception) {
        var field = exception.getBindingResult().getFieldError();
        String message = field == null ? ErrorCode.BAD_REQUEST.message() : field.getField() + " " + field.getDefaultMessage();
        return business(new BizException(ErrorCode.BAD_REQUEST, message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<ApiResponse<Void>> invalidInput(Exception exception) {
        return business(new BizException(ErrorCode.BAD_REQUEST));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> fileTooLarge() {
        return business(new BizException(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> databaseError(Exception exception) {
        LOG.error("数据库操作失败", exception);
        return business(new BizException(ErrorCode.INTERNAL_ERROR));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        LOG.error("未预期服务异常", exception);
        return business(new BizException(ErrorCode.INTERNAL_ERROR));
    }
}
