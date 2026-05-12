package work.jscraft.alt.dashboard.domain;

public enum SystemStatusCode {
    OK("ok"),
    DELAYED("delayed"),
    DOWN("down"),
    OFF_HOURS("off_hours");

    private final String wireValue;

    SystemStatusCode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
