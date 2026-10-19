import type { RiskLevel } from '@/types/risk';
import RiskBadge from '@/components/RiskBadge';
import { getRiskTheme, riskPanelClass } from '@/utils/risk-styles';

/** Warning banner: what is wrong (headline + level), at a glance. */
export default function RiskWarning({ level }: { level: RiskLevel }) {
  const theme = getRiskTheme(level);
  return (
    <div className={riskPanelClass(level)} role="alert">
      <div className="flex flex-wrap items-center gap-3">
        <RiskBadge level={level} />
        <h2 className="text-xl font-bold">{theme.headline}</h2>
      </div>
      <p className="mt-2 text-sm text-muted-foreground">
        Someone may be manipulating you into making this payment. Review the signals below
        before you decide what to do.
      </p>
    </div>
  );
}
