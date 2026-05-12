package work.jscraft.alt.news.application;

public class NewsGatewayException extends RuntimeException {

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

    public NewsGatewayException(Category category, String vendor, String message) {
        super(message);
        this.category = category;
        this.vendor = vendor;
    }

    public NewsGatewayException(Category category, String vendor, String message, Throwable cause) {
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
