package work.jscraft.alt.common.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PageRequestParams {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequestParams() {
    }

    public static Pageable resolve(Integer page, Integer size, Sort sort) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedPage < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size는 1 이상 100 이하여야 합니다.");
        }
        return PageRequest.of(resolvedPage - 1, resolvedSize, sort);
    }
}
