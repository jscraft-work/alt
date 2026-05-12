package work.jscraft.alt;

import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmRequest;
import work.jscraft.alt.trading.application.decision.TradingDecisionEngine;

class FakeTradingDecisionEngine implements TradingDecisionEngine {

    private LlmCallResult nextResult;
    private LlmRequest lastRequest;

    void resetAll() {
        nextResult = LlmCallResult.success(0, "{\"cycleStatus\":\"HOLD\",\"summary\":\"관망\"}", "");
        lastRequest = null;
    }

    void primeResult(LlmCallResult result) {
        this.nextResult = result;
    }

    void primeSuccess(String stdoutJson) {
        this.nextResult = LlmCallResult.success(0, stdoutJson, "");
    }

    LlmRequest lastRequest() {
        return lastRequest;
    }

    @Override
    public LlmCallResult requestTradingDecision(LlmRequest request) {
        lastRequest = request;
        if (nextResult == null) {
            return LlmCallResult.success(0, "{\"cycleStatus\":\"HOLD\",\"summary\":\"기본\"}", "");
        }
        return nextResult;
    }
}
