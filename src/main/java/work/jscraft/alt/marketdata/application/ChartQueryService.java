package work.jscraft.alt.marketdata.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.marketdata.application.ChartViews.MinuteBar;
import work.jscraft.alt.marketdata.application.ChartViews.MinuteBarsResponse;
import work.jscraft.alt.marketdata.application.ChartViews.OrderOverlay;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

@Service
@Transactional(readOnly = true)
public class ChartQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketMinuteItemRepository marketMinuteItemRepository;
    private final TradeOrderRepository tradeOrderRepository;

    public ChartQueryService(
            MarketMinuteItemRepository marketMinuteItemRepository,
            TradeOrderRepository tradeOrderRepository) {
        this.marketMinuteItemRepository = marketMinuteItemRepository;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    public MinuteBarsResponse getMinuteBars(String symbolCode, LocalDate date) {
        OffsetDateTime fromInclusive = date.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime toExclusive = date.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        List<MarketMinuteItemEntity> bars = marketMinuteItemRepository
                .findBySymbolCodeAndBarTimeBetweenOrderByBarTimeAsc(symbolCode, fromInclusive, toExclusive);
        List<MinuteBar> mapped = bars.stream()
                .map(bar -> new MinuteBar(
                        toKst(bar.getBarTime()),
                        bar.getOpenPrice(),
                        bar.getHighPrice(),
                        bar.getLowPrice(),
                        bar.getClosePrice(),
                        bar.getVolume()))
                .toList();
        return new MinuteBarsResponse(symbolCode, date, mapped);
    }

    public List<OrderOverlay> getOrderOverlays(String symbolCode, LocalDate date, UUID strategyInstanceId) {
        OffsetDateTime fromInclusive = date.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime toExclusive = date.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        List<TradeOrderEntity> orders = strategyInstanceId == null
                ? tradeOrderRepository.findByTradeOrderIntent_SymbolCodeAndRequestedAtBetweenOrderByRequestedAtAsc(
                        symbolCode, fromInclusive, toExclusive)
                : tradeOrderRepository
                        .findByTradeOrderIntent_SymbolCodeAndStrategyInstance_IdAndRequestedAtBetweenOrderByRequestedAtAsc(
                                symbolCode, strategyInstanceId, fromInclusive, toExclusive);
        return orders.stream()
                .map(order -> new OrderOverlay(
                        order.getId(),
                        order.getTradeOrderIntent().getSide(),
                        order.getOrderStatus(),
                        toKst(order.getRequestedAt()),
                        toKst(order.getFilledAt()),
                        order.getRequestedPrice(),
                        order.getAvgFilledPrice(),
                        order.getRequestedQuantity()))
                .toList();
    }

    private OffsetDateTime toKst(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(KST).toOffsetDateTime();
    }
}
