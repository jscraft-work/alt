import * as React from "react";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

/**
 * react-hook-form 과 함께 쓰는 단순 wrapper.
 *
 * shadcn 공식 `form` 컴포넌트는 별도 add 시 button.tsx 충돌이 있어 직접 구성한다.
 * - 라벨 + 입력 슬롯 + 인라인 에러 메시지를 한 묶음으로 렌더한다.
 */
interface FormFieldProps {
  id: string;
  label: React.ReactNode;
  required?: boolean;
  error?: string | undefined;
  helpText?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

export default function FormField({
  id,
  label,
  required,
  error,
  helpText,
  children,
  className,
}: FormFieldProps) {
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <Label htmlFor={id}>
        <span>{label}</span>
        {required && <span className="text-destructive">*</span>}
      </Label>
      {children}
      {error ? (
        <p className="text-xs text-destructive" role="alert">
          {error}
        </p>
      ) : helpText ? (
        <p className="text-xs text-muted-foreground">{helpText}</p>
      ) : null}
    </div>
  );
}
