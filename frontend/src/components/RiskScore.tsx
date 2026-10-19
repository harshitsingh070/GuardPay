import type { RiskLevel } from '@/types/risk';
import { cn } from '@/utils/cn';
import { getRiskTheme } from '@/utils/risk-styles';

/**
 * Prominent GuardPay Risk Score display. The score is a 0–100 heuristic —
 * it is NEVER shown as a fraud probability.
 */
export default function RiskScore({ score, level }: { score: number; level: RiskLevel }) {
  const theme = getRiskTheme(level);
  return (
    <div className="text-center">
      <p className="text-sm font-medium uppercase tracking-wide text-muted-foreground">
        GuardPay Risk Score
      </p>
      <p className={cn('mt-1 text-5xl font-bold tabular-nums', theme.score)} aria-label={`Risk score ${score} out of 100`}>
        {score} <span className="text-2xl font-medium text-muted-foreground">/ 100</span>
      </p>
    </div>
  );
}
