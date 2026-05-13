import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

/**
 * Phase 1에서는 라우트만 잡아두고 본 화면은 Phase 2 이후로 미룬다.
 * (docs/spec2.md §7.3 / §7.4 / §7.5 / §7.6)
 */
interface StubPageProps {
  title: string;
  description?: string;
}

export default function StubPage({ title, description }: StubPageProps) {
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">{title}</h1>
        {description && (
          <p className="text-sm text-muted-foreground">{description}</p>
        )}
      </header>
      <Card>
        <CardHeader>
          <CardTitle>준비 중 (Phase 2)</CardTitle>
          <CardDescription>해당 화면은 Phase 2 이후에 제공됩니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            준비 중 (Phase 2)
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
