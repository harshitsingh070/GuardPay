import { AlertTriangle, ShieldCheck } from 'lucide-react';
import type { RiskLevel } from '@/types/risk';
import RiskBadge from '@/components/RiskBadge';
import { getRiskTheme, riskPanelClass } from '@/utils/risk-styles';

/** Warning banner: what is wrong (headline + level), at a glance. */
export default function RiskWarning({ level }: { level: RiskLevel }) {
  const theme = getRiskTheme(level);
  const calm = level === 'LOW';
  const Icon = calm ? ShieldCheck : AlertTriangle;

  return (
    <div className={riskPanelClass(level)} role="alert">
      <div className="flex items-start gap-3">
        <span
          className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-white/70 ${theme.score}`}
        >
          <Icon className="h-5 w-5" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <RiskBadge level={level} />
            <h2 className="text-xl font-bold text-guardpay-ink">{theme.headline}</h2>
          </div>
          <p className="mt-2 text-sm leading-relaxed text-guardpay-muted">
            {calm
              ? 'Nothing in this payment context looked alarming. You can continue to PayPal Sandbox checkout.'
              : 'Someone may be manipulating you into making this payment. Review the signals below before you decide what to do.'}
          </p>
        </div>
      </div>
    </div>
  );
}
