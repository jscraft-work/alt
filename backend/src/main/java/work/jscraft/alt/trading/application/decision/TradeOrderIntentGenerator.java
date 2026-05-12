package work.jscraft.alt.trading.application.decision;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.trading.application.decision.ParsedDecision.ParsedOrder;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;

@Service
public class TradeOrderIntentGenerator {

    private final TradeOrderIntentRepository tradeOrderIntentRepository;

    public TradeOrderIntentGenerator(TradeOrderIntentRepository tradeOrderIntentRepository) {
        this.tradeOrderIntentRepository = tradeOrderIntentRepository;
    }

    @Transactional
    public List<TradeOrderIntentEntity> generate(TradeDecisionLogEntity decisionLog, ParsedDecision decision) {
        List<TradeOrderIntentEntity> created = new ArrayList<>();
        for (ParsedOrder order : decision.orders()) {
            TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
            intent.setTradeDecisionLog(decisionLog);
            intent.setSequenceNo(order.sequenceNo());
            intent.setSymbolCode(order.symbolCode());
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
}
