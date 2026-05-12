package work.jscraft.alt.trading.application.decision;

public class DecisionParseException extends RuntimeException {

    public enum Reason {
        INVALID_JSON,
        MISSING_FIELD,
        INVALID_STATUS,
        INVALID_ORDER,
        CONTENT_CONFLICT
    }

    private final Reason reason;

    public DecisionParseException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DecisionParseException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
