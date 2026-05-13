import { createContext } from "react";

export const STRATEGY_INSTANCE_SELECTION_STORAGE_KEY =
  "alt.selected-strategy-instance-id";

export interface StrategyInstanceSelectionContextValue {
  selectedInstanceId: string | null;
  setSelectedInstanceId: (strategyInstanceId: string | null) => void;
}

export const StrategyInstanceSelectionContext =
  createContext<StrategyInstanceSelectionContextValue | null>(null);

export function readInitialStrategyInstanceSelection(): string | null {
  if (typeof window === "undefined") {
    return null;
  }

  const value = window.localStorage.getItem(
    STRATEGY_INSTANCE_SELECTION_STORAGE_KEY,
  );

  return value ? value : null;
}
