import { useContext } from "react";
import { StrategyInstanceSelectionContext } from "@/context/strategy-instance-selection-context";

export function useStrategyInstanceSelection() {
  const context = useContext(StrategyInstanceSelectionContext);

  if (!context) {
    throw new Error(
      "useStrategyInstanceSelection must be used within StrategyInstanceSelectionProvider",
    );
  }

  return {
    selectedInstanceId: context.selectedInstanceId,
    isGlobalSelection: context.selectedInstanceId === null,
    setSelectedInstanceId: context.setSelectedInstanceId,
  };
}
