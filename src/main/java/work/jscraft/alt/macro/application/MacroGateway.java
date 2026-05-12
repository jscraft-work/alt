package work.jscraft.alt.macro.application;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

public interface MacroGateway {

    MacroSnapshot fetchDailyMacro(LocalDate baseDate);

    record MacroSnapshot(LocalDate baseDate, JsonNode payload) {
    }
}
