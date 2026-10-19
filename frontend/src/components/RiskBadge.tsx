import { AlertOctagon, AlertTriangle, ShieldAlert, ShieldCheck } from 'lucide-react';
import type { RiskLevel } from '@/types/risk';
import { cn } from '@/utils/cn';
import { getRiskTheme } from '@/utils/risk-styles';

const ICONS = {
  LOW: ShieldCheck,
  MEDIUM: ShieldAlert,
  HIGH: AlertTriangle,
  CRITICAL: AlertOctagon,
} as const;

/** Level pill — always pairs color with icon + text (never color-only). */
export default function RiskBadge({ level, className }: { level: RiskLevel; className?: string }) {
  const theme = getRiskTheme(level);
  const Icon = ICONS[level];
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold',
        theme.badge,
        className,
      )}
      role="status"
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
      {level}
    </span>
  );
}
