package work.jscraft.alt.trading.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import work.jscraft.alt.common.dto.ApiPagedResponse;
import work.jscraft.alt.common.dto.DateRangeDefaults;
import work.jscraft.alt.common.dto.PageRequestParams;
import work.jscraft.alt.trading.application.PublicTradeViews.OrderIntentView;
import work.jscraft.alt.trading.application.PublicTradeViews.OrderRefView;
import work.jscraft.alt.trading.application.PublicTradeViews.TradeDecisionDetail;
import work.jscraft.alt.trading.application.PublicTradeViews.TradeDecisionListItem;
import work.jscraft.alt.trading.application.PublicTradeViews.TradeOrderDetail;
import work.jscraft.alt.trading.application.PublicTradeViews.TradeOrderListItem;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

@Service
@Transactional(readOnly = true)
public class PublicTradeQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeDecisionLogRepository tradeDecisionLogRepository;
    private final TradeOrderIntentRepository tradeOrderIntentRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PublicTradeQueryService(
            TradeOrderRepository tradeOrderRepository,
            TradeDecisionLogRepository tradeDecisionLogRepository,
            TradeOrderIntentRepository tradeOrderIntentRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeDecisionLogRepository = tradeDecisionLogRepository;
        this.tradeOrderIntentRepository = tradeOrderIntentRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ApiPagedResponse<TradeOrderListItem> listTradeOrders(
            UUID strategyInstanceId,
            String symbolCode,
            String orderStatus,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer page,
            Integer size) {
        OffsetDateTime fromInclusive = DateRangeDefaults.resolveFromInclusive(dateFrom, clock);
        OffsetDateTime toExclusive = DateRangeDefaults.resolveToExclusive(dateTo, clock);

        Pageable pageable = PageRequestParams.resolve(page, size,
                Sort.by(Sort.Direction.DESC, "requestedAt"));

        Specification<TradeOrderEntity> spec = (root, query, cb) -> {
            Join<TradeOrderEntity, TradeOrderIntentEntity> intentJoin = root.join("tradeOrderIntent", JoinType.INNER);
            Predicate predicate = cb.between(root.get("requestedAt"), fromInclusive, toExclusive);
            if (strategyInstanceId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("strategyInstance").get("id"), strategyInstanceId));
            }
            if (symbolCode != null && !symbolCode.isBlank()) {
                predicate = cb.and(predicate, cb.equal(intentJoin.get("symbolCode"), symbolCode));
            }
            if (orderStatus != null && !orderStatus.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("orderStatus"), orderStatus));
            }
            return predicate;
        };

        Page<TradeOrderEntity> orders = tradeOrderRepository.findAll(spec, pageable);
        List<TradeOrderListItem> items = orders.getContent().stream()
                .map(this::toOrderListItem)
                .toList();
        return ApiPagedResponse.of(orders, items);
    }

    public TradeOrderDetail getTradeOrder(UUID tradeOrderId) {
        TradeOrderEntity order = tradeOrderRepository.findById(tradeOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."));
        TradeOrderIntentEntity intent = order.getTradeOrderIntent();
        return new TradeOrderDetail(
                order.getId(),
                intent.getId(),
                order.getStrategyInstance().getId(),
                intent.getSymbolCode(),
                intent.getSymbolName(),
                order.getClientOrderId(),
                order.getBrokerOrderNo(),
                order.getExecutionMode(),
                order.getOrderStatus(),
                order.getRequestedQuantity(),
                order.getRequestedPrice(),
                order.getFilledQuantity(),
                order.getAvgFilledPrice(),
                toKst(order.getRequestedAt()),
                toKst(order.getAcceptedAt()),
                toKst(order.getFilledAt()),
                order.getFailureReason(),
                order.getPortfolioAfterJson());
    }

    public ApiPagedResponse<TradeDecisionListItem> listTradeDecisions(
            UUID strategyInstanceId,
            String cycleStatus,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer page,
            Integer size) {
        OffsetDateTime fromInclusive = DateRangeDefaults.resolveFromInclusive(dateFrom, clock);
        OffsetDateTime toExclusive = DateRangeDefaults.resolveToExclusive(dateTo, clock);

        Pageable pageable = PageRequestParams.resolve(page, size,
                Sort.by(Sort.Direction.DESC, "cycleStartedAt"));

        Specification<TradeDecisionLogEntity> spec = (root, query, cb) -> {
            Predicate predicate = cb.between(root.get("cycleStartedAt"), fromInclusive, toExclusive);
            if (strategyInstanceId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("strategyInstance").get("id"), strategyInstanceId));
            }
            if (cycleStatus != null && !cycleStatus.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("cycleStatus"), cycleStatus));
            }
            return predicate;
        };

        Page<TradeDecisionLogEntity> decisions = tradeDecisionLogRepository.findAll(spec, pageable);
        List<TradeDecisionListItem> items = decisions.getContent().stream()
                .map(this::toDecisionListItem)
                .toList();
        return ApiPagedResponse.of(decisions, items);
    }

    public TradeDecisionDetail getTradeDecision(UUID tradeDecisionLogId) {
        TradeDecisionLogEntity decision = tradeDecisionLogRepository.findById(tradeDecisionLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "판단 로그를 찾을 수 없습니다."));

        List<TradeOrderIntentEntity> intents =
                tradeOrderIntentRepository.findByTradeDecisionLog_IdOrderBySequenceNoAsc(decision.getId());
        List<TradeOrderEntity> orders =
                tradeOrderRepository.findByTradeOrderIntent_TradeDecisionLog_IdOrderByRequestedAtAsc(decision.getId());

        List<OrderIntentView> intentViews = intents.stream()
                .map(intent -> new OrderIntentView(
                        intent.getId(),
                        intent.getSequenceNo(),
                        intent.getSymbolCode(),
                        intent.getSide(),
                        intent.getQuantity(),
                        intent.getOrderType(),
                        intent.getPrice(),
                        intent.getRationale(),
                        intent.getEvidenceJson(),
                        intent.getExecutionBlockedReason()))
                .toList();

        List<OrderRefView> orderViews = orders.stream()
                .map(order -> new OrderRefView(
                        order.getId(),
                        order.getTradeOrderIntent().getId(),
                        order.getOrderStatus(),
                        toKst(order.getRequestedAt())))
                .toList();

        JsonNode parsedDecision = buildParsedDecision(decision, intents);

        return new TradeDecisionDetail(
                decision.getId(),
                decision.getStrategyInstance().getId(),
                decision.getStrategyInstance().getName(),
                decision.getCycleStatus(),
                decision.getSummary(),
                decision.getConfidence(),
                decision.getFailureReason(),
                decision.getFailureDetail(),
                toKst(decision.getCycleStartedAt()),
                toKst(decision.getCycleFinishedAt()),
                decision.getRequestText(),
                decision.getResponseText(),
                decision.getStdoutText(),
                decision.getStderrText(),
                decision.getInputTokens(),
                decision.getOutputTokens(),
                decision.getEstimatedCost(),
                decision.getCallStatus(),
                decision.getEngineName(),
                decision.getModelName(),
                parsedDecision,
                decision.getSettingsSnapshotJson(),
                intentViews,
                orderViews);
    }

    private TradeOrderListItem toOrderListItem(TradeOrderEntity order) {
        TradeOrderIntentEntity intent = order.getTradeOrderIntent();
        return new TradeOrderListItem(
                order.getId(),
                order.getStrategyInstance().getId(),
                order.getStrategyInstance().getName(),
                intent.getSymbolCode(),
                intent.getSymbolName(),
                intent.getSide(),
                order.getExecutionMode(),
                order.getOrderStatus(),
                order.getRequestedQuantity(),
                order.getRequestedPrice(),
                order.getFilledQuantity(),
                order.getAvgFilledPrice(),
                toKst(order.getRequestedAt()),
                toKst(order.getFilledAt()));
    }

    private TradeDecisionListItem toDecisionListItem(TradeDecisionLogEntity decision) {
        long orderCount = tradeOrderRepository
                .findByTradeOrderIntent_TradeDecisionLog_IdOrderByRequestedAtAsc(decision.getId()).size();
        return new TradeDecisionListItem(
                decision.getId(),
                decision.getStrategyInstance().getId(),
                decision.getStrategyInstance().getName(),
                decision.getCycleStatus(),
                decision.getSummary(),
                decision.getConfidence(),
                decision.getFailureReason(),
                toKst(decision.getCycleStartedAt()),
                toKst(decision.getCycleFinishedAt()),
                (int) orderCount);
    }

    private JsonNode buildParsedDecision(TradeDecisionLogEntity decision, List<TradeOrderIntentEntity> intents) {
        var parsed = objectMapper.createObjectNode();
        parsed.put("cycleStatus", decision.getCycleStatus());
        parsed.put("summary", Optional.ofNullable(decision.getSummary()).orElse(""));
        var ordersArray = objectMapper.createArrayNode();
        for (TradeOrderIntentEntity intent : intents) {
            var order = objectMapper.createObjectNode();
            order.put("sequenceNo", intent.getSequenceNo());
            order.put("symbolCode", intent.getSymbolCode());
            order.put("side", intent.getSide());
            order.put("quantity", intent.getQuantity());
            order.put("orderType", intent.getOrderType());
            if (intent.getPrice() != null) {
                order.put("price", intent.getPrice());
            }
            order.put("rationale", intent.getRationale());
            order.set("evidence", intent.getEvidenceJson());
            ordersArray.add(order);
        }
        parsed.set("orders", ordersArray);
        return parsed;
    }

    private OffsetDateTime toKst(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(KST).toOffsetDateTime();
    }
}
