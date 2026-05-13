/**
 * 설정 페이지 공통 헤더 — docs/spec2.md §7.6.
 */
interface SettingsHeaderProps {
  title: string;
  description?: string;
  action?: React.ReactNode;
}

export default function SettingsHeader({
  title,
  description,
  action,
}: SettingsHeaderProps) {
  return (
    <header className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">{title}</h1>
        {description && (
          <p className="text-sm text-muted-foreground">{description}</p>
        )}
      </div>
      {action && <div className="flex shrink-0 items-center gap-2">{action}</div>}
    </header>
  );
}
