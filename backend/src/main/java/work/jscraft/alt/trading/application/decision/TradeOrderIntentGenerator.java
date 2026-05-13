package work.jscraft.alt.trading.application.decision;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.trading.application.decision.ParsedDecision.ParsedOrder;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;

@Service
public class TradeOrderIntentGenerator {

    private static final String UNKNOWN_SYMBOL_NAME = "(unknown)";

    private final TradeOrderIntentRepository tradeOrderIntentRepository;
    private final AssetMasterRepository assetMasterRepository;

    public TradeOrderIntentGenerator(
            TradeOrderIntentRepository tradeOrderIntentRepository,
            AssetMasterRepository assetMasterRepository) {
        this.tradeOrderIntentRepository = tradeOrderIntentRepository;
        this.assetMasterRepository = assetMasterRepository;
    }

    @Transactional
    public List<TradeOrderIntentEntity> generate(TradeDecisionLogEntity decisionLog, ParsedDecision decision) {
        List<TradeOrderIntentEntity> created = new ArrayList<>();
        for (ParsedOrder order : decision.orders()) {
            TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
            intent.setTradeDecisionLog(decisionLog);
            intent.setSequenceNo(order.sequenceNo());
            intent.setSymbolCode(order.symbolCode());
            intent.setSymbolName(resolveSymbolName(order.symbolCode()));
            intent.setSide(order.side());
            intent.setQuantity(order.quantity());
            intent.setOrderType(order.orderType());
            intent.setPrice(order.price());
            intent.setRationale(order.rationale());
            intent.setEvidenceJson(order.evidence());
            created.add(tradeOrderIntentRepository.saveAndFlush(intent));
        }
        return created;
    }

    private String resolveSymbolName(String symbolCode) {
        return assetMasterRepository.findBySymbolCode(symbolCode)
                .map(am -> am.getSymbolName())
                .orElse(UNKNOWN_SYMBOL_NAME);
    }
}
