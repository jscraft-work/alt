package work.jscraft.alt.common.error;

public class OptimisticLockConflictException extends ApiConflictException {

    private final long currentVersion;

    public OptimisticLockConflictException(long currentVersion) {
        super("OPTIMISTIC_LOCK_CONFLICT", "다른 사용자가 먼저 수정했습니다.");
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
