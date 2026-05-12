package work.jscraft.alt.trading.application.decision;

public record LlmCallResult(
        CallStatus callStatus,
        Integer exitCode,
        String stdoutText,
        String stderrText,
        String failureMessage) {

    public enum CallStatus {
        SUCCESS,
        TIMEOUT,
        AUTH_ERROR,
        NON_ZERO_EXIT,
        EMPTY_OUTPUT,
        INVALID_OUTPUT
    }

    public boolean isSuccess() {
        return callStatus == CallStatus.SUCCESS;
    }

    public static LlmCallResult success(int exitCode, String stdoutText, String stderrText) {
        return new LlmCallResult(CallStatus.SUCCESS, exitCode, stdoutText, stderrText, null);
    }

    public static LlmCallResult timeout(String message) {
        return new LlmCallResult(CallStatus.TIMEOUT, null, "", "", message);
    }

    public static LlmCallResult authError(int exitCode, String stdoutText, String stderrText) {
        return new LlmCallResult(CallStatus.AUTH_ERROR, exitCode, stdoutText, stderrText,
                "authentication error in stderr");
    }

    public static LlmCallResult nonZeroExit(int exitCode, String stdoutText, String stderrText) {
        return new LlmCallResult(CallStatus.NON_ZERO_EXIT, exitCode, stdoutText, stderrText,
                "non-zero exit code: " + exitCode);
    }

    public static LlmCallResult emptyOutput(int exitCode, String stderrText) {
        return new LlmCallResult(CallStatus.EMPTY_OUTPUT, exitCode, "", stderrText,
                "stdout is empty");
    }

    public static LlmCallResult invalidOutput(int exitCode, String stdoutText, String stderrText, String reason) {
        return new LlmCallResult(CallStatus.INVALID_OUTPUT, exitCode, stdoutText, stderrText, reason);
    }
}
