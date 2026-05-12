package work.jscraft.alt.common.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(ApiError error) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ApiError(code, message, List.of()));
    }

    public static ApiErrorResponse of(String code, String message, List<FieldErrorItem> fieldErrors) {
        return new ApiErrorResponse(new ApiError(code, message, fieldErrors));
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ApiError(String code, String message, List<FieldErrorItem> fieldErrors) {
    }

    public record FieldErrorItem(String field, String message) {
    }
}
