package work.jscraft.alt.ops.application;

public interface AlertGateway {

    DispatchResult dispatch(AlertEvent event);

    enum DispatchResult {
        SENT,
        SKIPPED_DISABLED,
        SKIPPED_QUIET_HOURS,
        FAILED
    }
}
