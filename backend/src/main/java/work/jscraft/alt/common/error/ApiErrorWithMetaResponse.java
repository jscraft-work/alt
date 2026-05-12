package work.jscraft.alt.common.error;

import java.util.List;
import java.util.Map;

public record ApiErrorWithMetaResponse(ApiErrorResponse.ApiError error, Map<String, Object> meta) {

    public static ApiErrorWithMetaResponse of(String code, String message, Map<String, Object> meta) {
        return new ApiErrorWithMetaResponse(new ApiErrorResponse.ApiError(code, message, List.of()), meta);
    }
}
