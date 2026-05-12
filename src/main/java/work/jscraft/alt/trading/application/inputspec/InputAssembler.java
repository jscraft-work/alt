package work.jscraft.alt.trading.application.inputspec;

import java.util.List;

import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;

public interface InputAssembler {

    InputAssembly assemble(SettingsSnapshot snapshot);

    record InputAssembly(List<String> targetSymbols, String mode) {
    }
}
