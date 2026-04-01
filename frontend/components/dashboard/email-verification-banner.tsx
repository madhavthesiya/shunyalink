import { Loader2, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";

export function EmailVerificationBanner({
  onResend,
  isResending,
}: {
  onResend: () => void;
  isResending: boolean;
}) {
  return (
    <div className="relative group overflow-hidden rounded-2xl border border-amber-500/20 bg-amber-500/5 p-5 sm:p-6 backdrop-blur-xl animate-in fade-in slide-in-from-top-4 duration-500">
      <div className="absolute inset-0 bg-gradient-to-r from-amber-500/10 via-transparent to-amber-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-700" />
      <div className="relative flex flex-col sm:flex-row items-center justify-between gap-6">
        <div className="flex items-center gap-4 text-center sm:text-left">
          <div className="relative flex-shrink-0">
            <div className="absolute inset-0 bg-amber-500 blur-lg opacity-20 animate-pulse" />
            <div className="relative w-12 h-12 rounded-full bg-amber-500/20 flex items-center justify-center border border-amber-500/30">
              <AlertCircle className="w-6 h-6 text-amber-500" />
            </div>
          </div>
          <div>
            <h3 className="text-lg font-bold text-foreground">
              Verify Your Email Address
            </h3>
            <p className="text-sm text-muted-foreground mt-1 leading-relaxed">
              Please verify your email to unlock all features like{" "}
              <span className="text-foreground font-medium">
                password-protected links
              </span>{" "}
              and{" "}
              <span className="text-foreground font-medium">expiry dates</span>.
            </p>
          </div>
        </div>
        <Button
          onClick={onResend}
          disabled={isResending}
          className="w-full sm:w-auto bg-amber-500 hover:bg-amber-600 text-white font-bold px-8 shadow-lg shadow-amber-500/25 transition-all duration-300 hover:scale-105 active:scale-95 disabled:opacity-70"
        >
          {isResending ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Resending...
            </>
          ) : (
            "Resend Link"
          )}
        </Button>
      </div>
    </div>
  );
}
