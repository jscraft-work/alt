package work.jscraft.alt.trading.domain;

import java.util.List;
import java.util.Locale;

public enum TradeOrderStatus {
    REQUESTED("requested", true, false, false),
    SUBMISSION_UNKNOWN("submission_unknown", true, false, false),
    ACCEPTED("accepted", true, false, false),
    PARTIAL("partial", true, false, false),
    FILLED("filled", false, true, false),
    REJECTED("rejected", false, true, true),
    FAILED("failed", false, true, true),
    CANCELED("canceled", false, true, false);

    private final String wireValue;
    private final boolean pendingReconcile;
    private final boolean terminal;
    private final boolean terminalFailure;

    TradeOrderStatus(String wireValue, boolean pendingReconcile, boolean terminal, boolean terminalFailure) {
        this.wireValue = wireValue;
        this.pendingReconcile = pendingReconcile;
        this.terminal = terminal;
        this.terminalFailure = terminalFailure;
    }

    public String wireValue() {
        return wireValue;
    }

    public boolean isPendingReconcile() {
        return pendingReconcile;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isTerminalFailure() {
        return terminalFailure;
    }

    public static TradeOrderStatus fromWire(String wireValue) {
        if (wireValue == null || wireValue.isBlank()) {
            throw new IllegalArgumentException("order status is blank");
        }
        String normalized = wireValue.toLowerCase(Locale.ROOT);
        for (TradeOrderStatus status : values()) {
            if (status.wireValue.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown order status: " + wireValue);
    }

    public static List<String> pendingWireValues() {
        return List.of(
                REQUESTED.wireValue,
                SUBMISSION_UNKNOWN.wireValue,
                ACCEPTED.wireValue,
                PARTIAL.wireValue);
    }
}
