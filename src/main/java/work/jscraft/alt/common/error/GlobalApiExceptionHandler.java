package work.jscraft.alt.common.error;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_ERROR",
                "요청 값이 올바르지 않습니다.",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(fieldError -> new ApiErrorResponse.FieldErrorItem(fieldError.getField(),
                                fieldError.getDefaultMessage()))
                        .toList()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException exception) {
        List<ApiErrorResponse.FieldErrorItem> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiErrorResponse.FieldErrorItem(fieldError.getField(),
                        fieldError.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_ERROR",
                "요청 값이 올바르지 않습니다.",
                fieldErrors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_ERROR",
                "필수 요청 파라미터가 누락되었습니다.",
                List.of(new ApiErrorResponse.FieldErrorItem(exception.getParameterName(), "필수 값입니다."))));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_ERROR",
                "요청 파라미터 형식이 올바르지 않습니다.",
                List.of(new ApiErrorResponse.FieldErrorItem(exception.getName(), "형식이 올바르지 않습니다."))));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? exception.getStatusCode().toString() : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiErrorResponse.of(resolveCode(exception.getStatusCode().value()), message));
    }

    @ExceptionHandler(OptimisticLockConflictException.class)
    public ResponseEntity<ApiErrorWithMetaResponse> handleOptimisticLockConflict(OptimisticLockConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorWithMetaResponse.of(
                        exception.getCode(),
                        exception.getMessage(),
                        Map.of("currentVersion", exception.getCurrentVersion())));
    }

    @ExceptionHandler(ApiConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleApiConflict(ApiConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleObjectOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("OPTIMISTIC_LOCK_CONFLICT", "다른 사용자가 먼저 수정했습니다."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("REQUEST_CONFLICT", "요청을 처리할 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }

    private String resolveCode(int statusCode) {
        return switch (statusCode) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "REQUEST_CONFLICT";
            case 429 -> "TOO_MANY_REQUESTS";
            default -> "REQUEST_ERROR";
        };
    }
}
