package work.jscraft.alt.macro.application;

public class MacroGatewayException extends RuntimeException {

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

    public MacroGatewayException(Category category, String vendor, String message) {
        super(message);
        this.category = category;
        this.vendor = vendor;
    }

    public MacroGatewayException(Category category, String vendor, String message, Throwable cause) {
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
