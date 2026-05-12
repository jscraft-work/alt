package work.jscraft.alt.interfaces.api.publicapi;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.common.dto.ApiPagedResponse;
import work.jscraft.alt.trading.application.PublicTradeQueryService;
import work.jscraft.alt.trading.application.PublicTradeViews.TradeDecisionDetail;
import work.jscraft.alt.trading.application.PublicTradeViews.TradeDecisionListItem;

@RestController
@RequestMapping("/api/trade-decisions")
public class TradeDecisionController {

    private final PublicTradeQueryService publicTradeQueryService;

    public TradeDecisionController(PublicTradeQueryService publicTradeQueryService) {
        this.publicTradeQueryService = publicTradeQueryService;
    }

    @GetMapping
    public ApiPagedResponse<TradeDecisionListItem> listTradeDecisions(
            @RequestParam(required = false) UUID strategyInstanceId,
            @RequestParam(required = false) String cycleStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return publicTradeQueryService.listTradeDecisions(
                strategyInstanceId, cycleStatus, dateFrom, dateTo, page, size);
    }

    @GetMapping("/{tradeDecisionLogId}")
    public ApiDataResponse<TradeDecisionDetail> getTradeDecision(@PathVariable UUID tradeDecisionLogId) {
        return new ApiDataResponse<>(publicTradeQueryService.getTradeDecision(tradeDecisionLogId));
    }
}
