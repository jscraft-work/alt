package work.jscraft.alt;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import work.jscraft.alt.macro.application.MacroGateway;
import work.jscraft.alt.macro.application.MacroGatewayException;

class FakeMacroGateway implements MacroGateway {

    private final Map<LocalDate, MacroSnapshot> responses = new HashMap<>();
    private final Map<LocalDate, MacroGatewayException> failures = new HashMap<>();

    void resetAll() {
        responses.clear();
        failures.clear();
    }

    void primeMacro(LocalDate baseDate, MacroSnapshot snapshot) {
        responses.put(baseDate, snapshot);
        failures.remove(baseDate);
    }

    void primeFailure(LocalDate baseDate, MacroGatewayException exception) {
        failures.put(baseDate, exception);
        responses.remove(baseDate);
    }

    @Override
    public MacroSnapshot fetchDailyMacro(LocalDate baseDate) {
        MacroGatewayException failure = failures.get(baseDate);
        if (failure != null) {
            throw failure;
        }
        MacroSnapshot snapshot = responses.get(baseDate);
        if (snapshot == null) {
            throw new MacroGatewayException(MacroGatewayException.Category.EMPTY_RESPONSE, "fake",
                    "no fake macro for " + baseDate);
        }
        return snapshot;
    }
}
