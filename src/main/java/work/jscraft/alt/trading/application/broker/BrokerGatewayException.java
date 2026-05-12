package work.jscraft.alt.trading.application.broker;

public class BrokerGatewayException extends RuntimeException {

    public enum Category {
        AUTH_FAILED,
        REQUEST_INVALID,
        TRANSIENT,
        TIMEOUT,
        INVALID_RESPONSE,
        EMPTY_RESPONSE,
        ORDER_NOT_FOUND
    }

    private final Category category;
    private final String vendor;

    public BrokerGatewayException(Category category, String vendor, String message) {
        super(message);
        this.category = category;
        this.vendor = vendor;
    }

    public BrokerGatewayException(Category category, String vendor, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.vendor = vendor;
    }

    public Category getCategory() {
        return category;
    }

    public String getVendor() {
        return vendor;
    }
}
