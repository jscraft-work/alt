import { useEffect, useState, type ReactNode } from "react";
import {
  readInitialStrategyInstanceSelection,
  STRATEGY_INSTANCE_SELECTION_STORAGE_KEY,
  StrategyInstanceSelectionContext,
} from "@/context/strategy-instance-selection-context";

export function StrategyInstanceSelectionProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(
    readInitialStrategyInstanceSelection,
  );

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    if (selectedInstanceId) {
      window.localStorage.setItem(
        STRATEGY_INSTANCE_SELECTION_STORAGE_KEY,
        selectedInstanceId,
      );
      return;
    }

    window.localStorage.removeItem(STRATEGY_INSTANCE_SELECTION_STORAGE_KEY);
  }, [selectedInstanceId]);

  return (
    <StrategyInstanceSelectionContext.Provider
      value={{ selectedInstanceId, setSelectedInstanceId }}
    >
      {children}
    </StrategyInstanceSelectionContext.Provider>
  );
}
