import * as React from "react";
import { AlertCircle, CheckCircle2 } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { cn } from "@/lib/utils";

type InlineAlertVariant = "destructive" | "success" | "warning";

const variantStyles: Record<
  InlineAlertVariant,
  { alert: string; icon: React.ReactNode }
> = {
  destructive: {
    alert:
      "border-destructive/20 bg-destructive/10 text-destructive [&>svg]:text-destructive",
    icon: <AlertCircle className="size-4" />,
  },
  success: {
    alert:
      "border-success/20 bg-success/10 text-success [&>svg]:text-success [&_[data-slot=alert-description]]:text-success/90",
    icon: <CheckCircle2 className="size-4" />,
  },
  warning: {
    alert:
      "border-amber-500/20 bg-amber-500/10 text-amber-700 dark:text-amber-400 [&>svg]:text-amber-600 dark:[&>svg]:text-amber-400",
    icon: <AlertCircle className="size-4" />,
  },
};

export function InlineAlert({
  variant,
  title,
  children,
  className,
  icon,
}: {
  variant: InlineAlertVariant;
  title?: string;
  children: React.ReactNode;
  className?: string;
  icon?: React.ReactNode;
}) {
  const styles = variantStyles[variant];
  const useIcon = icon ?? styles.icon;

  if (variant === "destructive") {
    return (
      <Alert
        variant="destructive"
        className={cn(
          "border-destructive/20 bg-destructive/10 text-destructive [&>svg]:text-destructive",
          "animate-in fade-in-0 slide-in-from-bottom-4 duration-300",
          className,
        )}
      >
        {useIcon}
        {title != null ? (
          <>
            <AlertTitle>{title}</AlertTitle>
            <AlertDescription>{children}</AlertDescription>
          </>
        ) : (
          <AlertDescription className="col-start-2">{children}</AlertDescription>
        )}
      </Alert>
    );
  }

  return (
    <Alert
      className={cn(
        styles.alert,
        "animate-in fade-in-0 slide-in-from-bottom-4 duration-300",
        className,
      )}
    >
      {useIcon}
      {title != null ? (
        <>
          <AlertTitle className="text-inherit">{title}</AlertTitle>
          <AlertDescription className="text-inherit/90">{children}</AlertDescription>
        </>
      ) : (
        <AlertDescription className="col-start-2 text-inherit">
          {children}
        </AlertDescription>
      )}
    </Alert>
  );
}
