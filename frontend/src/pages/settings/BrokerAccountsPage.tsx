import { useState } from "react";
import { Loader2, Pencil, Plus } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import SettingsHeader from "@/components/settings/SettingsHeader";
import BrokerAccountDialog from "@/components/settings/BrokerAccountDialog";
import { useBrokerAccounts } from "@/hooks/use-broker-accounts";
import { formatKstDateTime } from "@/lib/format";
import type { BrokerAccount } from "@/lib/api-types";

/**
 * 브로커 계좌 — docs/04-api-spec.md §8.17~8.18, spec2.md §7.6.
 * live 계좌 식별 정보는 §10.8 정책에 따라 마스킹된 값을 표시한다.
 */
export default function BrokerAccountsPage() {
  const { data, isLoading, error } = useBrokerAccounts();
  const [dialog, setDialog] = useState<
    | { mode: "create" }
    | { mode: "edit"; account: BrokerAccount }
    | null
  >(null);

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title="브로커 계좌"
        description="live 모드 인스턴스에 연결할 브로커 계좌를 관리합니다."
        action={
          <Button onClick={() => setDialog({ mode: "create" })}>
            <Plus className="size-4" /> 추가
          </Button>
        }
      />

      {isLoading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중...
        </div>
      ) : error ? (
        <p className="text-sm text-destructive">{error.message}</p>
      ) : !data || data.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          등록된 브로커 계좌가 없습니다.
        </p>
      ) : (
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>별칭</TableHead>
                <TableHead>브로커</TableHead>
                <TableHead>계좌번호</TableHead>
                <TableHead>활성</TableHead>
                <TableHead>수정일</TableHead>
                <TableHead className="w-[1%]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="font-medium">{row.alias}</TableCell>
                  <TableCell>{row.brokerCode}</TableCell>
                  <TableCell className="font-mono text-xs">
                    {row.accountNumberMasked}
                  </TableCell>
                  <TableCell>
                    {row.enabled ? (
                      <Badge>ON</Badge>
                    ) : (
                      <Badge variant="secondary">OFF</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatKstDateTime(row.updatedAt)}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="수정"
                      onClick={() =>
                        setDialog({ mode: "edit", account: row })
                      }
                    >
                      <Pencil className="size-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <BrokerAccountDialog
        key={dialog ? (dialog.mode === "edit" ? dialog.account.id : "create") : "closed"}
        open={dialog !== null}
        mode={dialog?.mode ?? "create"}
        account={dialog?.mode === "edit" ? dialog.account : undefined}
        onOpenChange={(open) => {
          if (!open) setDialog(null);
        }}
        onSuccess={(action) => {
          toast.success(
            action === "create"
              ? "계좌가 추가되었습니다."
              : "계좌가 수정되었습니다.",
          );
          setDialog(null);
        }}
      />
    </div>
  );
}
