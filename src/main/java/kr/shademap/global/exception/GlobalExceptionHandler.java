package kr.shademap.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import kr.shademap.global.response.ProblemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemResponse> api(ApiException exception, HttpServletRequest request) {
        return problem(request, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemResponse> input(Exception exception, HttpServletRequest request) {
        return problem(request, ErrorCode.INVALID_INPUT, "요청 항목과 값의 형식을 확인해주세요.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemResponse> database(DataAccessException exception, HttpServletRequest request) {
        logFailure(exception, request);
        return problem(request, ErrorCode.RESOURCE_UNAVAILABLE,
                "자료 조회가 일시적으로 불가능합니다. 잠시 후 다시 시도해주세요.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemResponse> notFound(Exception exception, HttpServletRequest request) {
        return problem(request, ErrorCode.NOT_FOUND, "요청한 API가 없습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemResponse> methodNotAllowed(Exception exception, HttpServletRequest request) {
        return problem(request, ErrorCode.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemResponse> unexpected(Exception exception, HttpServletRequest request) {
        logFailure(exception, request);
        return problem(request, ErrorCode.INTERNAL_ERROR, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }

    private void logFailure(Exception exception, HttpServletRequest request) {
        // SQL, connection strings and credentials must not be logged or returned.
        log.warn("API request failed; requestId={}, type={}",
                request.getAttribute("requestId"), exception.getClass().getSimpleName());
    }

    private ResponseEntity<ProblemResponse> problem(
            HttpServletRequest request, ErrorCode errorCode, String message
    ) {
        String code = errorCode.code();
        var response = new ProblemResponse(
                "urn:shade-map:problem:" + code.toLowerCase(Locale.ROOT),
                code, errorCode.status(), code, message,
                (String) request.getAttribute("requestId"), List.of()
        );
        return ResponseEntity.status(errorCode.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(response);
    }
}
