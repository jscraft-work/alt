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
import work.jscraft.alt.trading.application.PublicTradeViews.TradeOrderDetail;
import work.jscraft.alt.trading.application.PublicTradeViews.TradeOrderListItem;

@RestController
@RequestMapping("/api/trade-orders")
public class TradeOrderController {

    private final PublicTradeQueryService publicTradeQueryService;

    public TradeOrderController(PublicTradeQueryService publicTradeQueryService) {
        this.publicTradeQueryService = publicTradeQueryService;
    }

    @GetMapping
    public ApiPagedResponse<TradeOrderListItem> listTradeOrders(
            @RequestParam(required = false) UUID strategyInstanceId,
            @RequestParam(required = false) String symbolCode,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return publicTradeQueryService.listTradeOrders(
                strategyInstanceId, symbolCode, orderStatus, dateFrom, dateTo, page, size);
    }

    @GetMapping("/{tradeOrderId}")
    public ApiDataResponse<TradeOrderDetail> getTradeOrder(@PathVariable UUID tradeOrderId) {
        return new ApiDataResponse<>(publicTradeQueryService.getTradeOrder(tradeOrderId));
    }
}
