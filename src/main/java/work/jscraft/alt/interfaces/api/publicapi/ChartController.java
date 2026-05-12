package work.jscraft.alt.interfaces.api.publicapi;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new ApiDataResponse<>(chartQueryService.getMinuteBars(symbolCode, date));
    }

    @GetMapping("/order-overlays")
    public ApiDataResponse<List<OrderOverlay>> orderOverlays(
            @RequestParam String symbolCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID strategyInstanceId) {
        return new ApiDataResponse<>(chartQueryService.getOrderOverlays(symbolCode, date, strategyInstanceId));
    }
}
