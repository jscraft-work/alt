package work.jscraft.alt.disclosure.application;

public class DisclosureGatewayException extends RuntimeException {

    public enum Category {
        AUTH_FAILED,
        REQUEST_INVALID,
        TRANSIENT,
        TIMEOUT,
        INVALID_RESPONSE,
        EMPTY_RESPONSE
    }

    private final Category category;
    private final String vendor;

    public DisclosureGatewayException(Category category, String vendor, String message) {
        super(message);
        this.category = category;
        this.vendor = vendor;
    }

    public DisclosureGatewayException(Category category, String vendor, String message, Throwable cause) {
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
