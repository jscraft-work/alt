package work.jscraft.alt.interfaces.api.publicapi;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.marketdata.application.ChartQueryService;
import work.jscraft.alt.marketdata.application.ChartViews.MinuteBarsResponse;
import work.jscraft.alt.marketdata.application.ChartViews.OrderOverlay;

@RestController
@RequestMapping("/api/charts")
public class ChartController {

    private final ChartQueryService chartQueryService;

    public ChartController(ChartQueryService chartQueryService) {
        this.chartQueryService = chartQueryService;
    }

    @GetMapping("/minutes")
    public ApiDataResponse<MinuteBarsResponse> minutes(
            @RequestParam String symbolCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        DateRange range = resolveRange(date, dateFrom, dateTo);
        return new ApiDataResponse<>(chartQueryService.getMinuteBars(symbolCode, range.dateFrom(), range.dateTo()));
    }

    @GetMapping("/order-overlays")
    public ApiDataResponse<List<OrderOverlay>> orderOverlays(
            @RequestParam String symbolCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID strategyInstanceId) {
        DateRange range = resolveRange(date, dateFrom, dateTo);
        return new ApiDataResponse<>(chartQueryService.getOrderOverlays(
                symbolCode,
                range.dateFrom(),
                range.dateTo(),
                strategyInstanceId));
    }

    private DateRange resolveRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        if (date != null) {
            if (dateFrom != null || dateTo != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "date와 dateFrom/dateTo를 함께 사용할 수 없습니다.");
            }
            return new DateRange(date, date);
        }
        if (dateFrom == null || dateTo == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "date 또는 dateFrom/dateTo를 지정해야 합니다.");
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "dateFrom은 dateTo보다 늦을 수 없습니다.");
        }
        return new DateRange(dateFrom, dateTo);
    }

    private record DateRange(LocalDate dateFrom, LocalDate dateTo) {
    }
}
