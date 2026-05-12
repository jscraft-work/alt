package work.jscraft.alt.dashboard.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.dashboard.application.DashboardViews.InstanceDashboardView;
import work.jscraft.alt.dashboard.application.DashboardViews.InstanceSummary;
import work.jscraft.alt.dashboard.application.DashboardViews.LatestDecisionView;
import work.jscraft.alt.dashboard.application.DashboardViews.PortfolioSummary;
import work.jscraft.alt.dashboard.application.DashboardViews.PositionView;
import work.jscraft.alt.dashboard.application.DashboardViews.RecentOrderView;
import work.jscraft.alt.dashboard.application.DashboardViews.StrategyOverviewCardView;
import work.jscraft.alt.dashboard.application.DashboardViews.SystemStatusEntryView;
import work.jscraft.alt.dashboard.domain.ServiceHealthSnapshot;
import work.jscraft.alt.dashboard.domain.SystemStatusClassifier;
import work.jscraft.alt.dashboard.domain.SystemStatusEvaluation;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    public static final String LIFECYCLE_STATE_PATTERN = "draft|active|inactive";
    public static final Pattern LIFECYCLE_STATE_REGEX = Pattern.compile(LIFECYCLE_STATE_PATTERN);

    static final String STATUS_OK = "ok";
    static final String STATUS_DOWN = "down";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Sort UPDATED_AT_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final List<MonitoredService> MONITORED_SERVICES = List.of(
            new MonitoredService("marketdata", true),
            new MonitoredService("news", false),
            new MonitoredService("disclosure", false),
            new MonitoredService("macro", false),
            new MonitoredService("brokerage", true));

    private final StrategyInstanceRepository strategyInstanceRepository;
    private final StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final TradeDecisionLogRepository tradeDecisionLogRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final OpsEventRepository opsEventRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final SystemStatusClassifier systemStatusClassifier;
    private final Clock clock;

    public DashboardQueryService(
            StrategyInstanceRepository strategyInstanceRepository,
            StrategyInstanceWatchlistRelationRepository watchlistRelationRepository,
            PortfolioRepository portfolioRepository,
            PortfolioPositionRepository portfolioPositionRepository,
            TradeDecisionLogRepository tradeDecisionLogRepository,
            TradeOrderRepository tradeOrderRepository,
            OpsEventRepository opsEventRepository,
            AssetMasterRepository assetMasterRepository,
            SystemStatusClassifier systemStatusClassifier,
            Clock clock) {
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.watchlistRelationRepository = watchlistRelationRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.tradeDecisionLogRepository = tradeDecisionLogRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.opsEventRepository = opsEventRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.systemStatusClassifier = systemStatusClassifier;
        this.clock = clock;
    }

    public List<StrategyOverviewCardView> listOverviewCards(String lifecycleState, Boolean autoPaused) {
        if (lifecycleState != null && !LIFECYCLE_STATE_REGEX.matcher(lifecycleState).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lifecycleState가 올바르지 않습니다.");
        }
        return strategyInstanceRepository.findAll(UPDATED_AT_DESC).stream()
                .filter(entity -> lifecycleState == null || lifecycleState.equals(entity.getLifecycleState()))
                .filter(entity -> autoPaused == null || autoPaused.equals(entity.getAutoPausedReason() != null))
                .map(this::toOverviewCard)
                .toList();
    }

    public InstanceDashboardView getInstanceDashboard(UUID strategyInstanceId) {
        StrategyInstanceEntity instance = strategyInstanceRepository.findById(strategyInstanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "전략 인스턴스를 찾을 수 없습니다."));

        Optional<PortfolioEntity> portfolio = portfolioRepository.findByStrategyInstanceId(strategyInstanceId);
        List<PortfolioPositionEntity> positions = portfolioPositionRepository.findByStrategyInstanceId(strategyInstanceId);
        Optional<TradeDecisionLogEntity> latestDecision =
                tradeDecisionLogRepository.findFirstByStrategyInstance_IdOrderByCycleStartedAtDesc(strategyInstanceId);
        List<TradeOrderEntity> recentOrders = tradeOrderRepository.findByStrategyInstance_IdOrderByRequestedAtDesc(
                strategyInstanceId, Limit.of(20));

        InstanceSummary summary = new InstanceSummary(
                instance.getId(),
                instance.getName(),
                instance.getExecutionMode(),
                instance.getLifecycleState(),
                instance.getAutoPausedReason(),
                instance.getBudgetAmount(),
                instance.getBrokerAccount() != null ? instance.getBrokerAccount().getAccountMasked() : null);

        PortfolioSummary portfolioSummary = portfolio
                .map(p -> new PortfolioSummary(p.getCashAmount(), p.getTotalAssetAmount(), p.getRealizedPnlToday()))
                .orElseGet(() -> new PortfolioSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        List<PositionView> positionViews = positions.stream()
                .sorted(Comparator.comparing(PortfolioPositionEntity::getSymbolCode))
                .map(position -> {
                    String symbolName = assetMasterRepository.findBySymbolCode(position.getSymbolCode())
                            .map(AssetMasterEntity::getSymbolName)
                            .orElse(position.getSymbolCode());
                    return new PositionView(
                            position.getSymbolCode(),
                            symbolName,
                            position.getQuantity(),
                            position.getAvgBuyPrice(),
                            position.getLastMarkPrice(),
                            position.getUnrealizedPnl());
                })
                .toList();

        LatestDecisionView latestDecisionView = latestDecision
                .map(decision -> new LatestDecisionView(
                        decision.getId(),
                        decision.getCycleStatus(),
                        decision.getSummary(),
                        toKst(decision.getCycleStartedAt())))
                .orElse(null);

        List<RecentOrderView> recentOrderViews = recentOrders.stream()
                .map(order -> new RecentOrderView(
                        order.getId(),
                        order.getTradeOrderIntent().getSymbolCode(),
                        order.getTradeOrderIntent().getSide(),
                        order.getOrderStatus(),
                        toKst(order.getRequestedAt()),
                        order.getRequestedQuantity(),
                        order.getRequestedPrice()))
                .toList();

        return new InstanceDashboardView(
                summary,
                portfolioSummary,
                positionViews,
                listSystemStatus(),
                latestDecisionView,
                recentOrderViews);
    }

    public List<SystemStatusEntryView> listSystemStatus() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return MONITORED_SERVICES.stream()
                .map(service -> classifyService(service, now))
                .toList();
    }

    private SystemStatusEntryView classifyService(MonitoredService service, OffsetDateTime now) {
        Optional<OpsEventEntity> lastSuccess =
                opsEventRepository.findFirstByServiceNameAndStatusCodeOrderByOccurredAtDesc(service.name(), STATUS_OK);
        Optional<OpsEventEntity> lastFailure =
                opsEventRepository.findFirstByServiceNameAndStatusCodeOrderByOccurredAtDesc(service.name(), STATUS_DOWN);
        ServiceHealthSnapshot snapshot = new ServiceHealthSnapshot(
                service.name(),
                service.marketSensitive(),
                lastSuccess.map(OpsEventEntity::getOccurredAt).orElse(null),
                lastFailure.map(OpsEventEntity::getOccurredAt).orElse(null),
                lastFailure.map(OpsEventEntity::getMessage).orElse(null));
        SystemStatusEvaluation evaluation = systemStatusClassifier.classify(snapshot, now);
        return new SystemStatusEntryView(
                evaluation.serviceName(),
                evaluation.statusCode().wireValue(),
                evaluation.message(),
                toKst(evaluation.occurredAt()),
                toKst(evaluation.lastSuccessAt()));
    }

    private StrategyOverviewCardView toOverviewCard(StrategyInstanceEntity entity) {
        Optional<PortfolioEntity> portfolio = portfolioRepository.findByStrategyInstanceId(entity.getId());
        Optional<TradeDecisionLogEntity> latestDecision =
                tradeDecisionLogRepository.findFirstByStrategyInstance_IdOrderByCycleStartedAtDesc(entity.getId());
        long watchlistCount = watchlistRelationRepository.countByStrategyInstanceId(entity.getId());
        return new StrategyOverviewCardView(
                entity.getId(),
                entity.getName(),
                entity.getExecutionMode(),
                entity.getLifecycleState(),
                entity.getAutoPausedReason(),
                entity.getBudgetAmount(),
                portfolio.map(PortfolioEntity::getCashAmount).orElse(BigDecimal.ZERO),
                portfolio.map(PortfolioEntity::getTotalAssetAmount).orElse(BigDecimal.ZERO),
                portfolio.map(PortfolioEntity::getRealizedPnlToday).orElse(BigDecimal.ZERO),
                latestDecision.map(TradeDecisionLogEntity::getCycleStatus).orElse(null),
                latestDecision.map(decision -> toKst(decision.getCycleStartedAt())).orElse(null),
                watchlistCount);
    }

    private OffsetDateTime toKst(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(KST).toOffsetDateTime();
    }

    private record MonitoredService(String name, boolean marketSensitive) {
    }
}
