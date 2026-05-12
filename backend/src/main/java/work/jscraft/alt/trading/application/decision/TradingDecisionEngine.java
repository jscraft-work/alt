package work.jscraft.alt.trading.application.decision;

public interface TradingDecisionEngine {

    LlmCallResult requestTradingDecision(LlmRequest request);
}
