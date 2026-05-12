package work.jscraft.alt.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record ApiPagedResponse<T>(List<T> data, PageMeta meta) {

    public record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    public static <T> ApiPagedResponse<T> of(Page<T> page) {
        PageMeta meta = new PageMeta(
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
        return new ApiPagedResponse<>(page.getContent(), meta);
    }

    public static <T, R> ApiPagedResponse<R> of(Page<T> page, List<R> mapped) {
        PageMeta meta = new PageMeta(
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
        return new ApiPagedResponse<>(mapped, meta);
    }
}
