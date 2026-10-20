import type { RiskLevel } from '@/types/risk';
import { cn } from '@/utils/cn';

export interface RiskTheme {
  /** Card/panel treatment. */
  panel: string;
  /** Level pill treatment. */
  badge: string;
  /** Big score treatment. */
  score: string;
  /** Short headline, e.g. "Potential payment scam". Never color-only. */
  headline: string;
}

const THEMES: Record<RiskLevel, RiskTheme> = {
  LOW: {
    panel: 'border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950',
    badge: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-100',
    score: 'text-green-700 dark:text-green-300',
    headline: 'Low-risk payment',
  },
  MEDIUM: {
    panel: 'border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950',
    badge: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-100',
    score: 'text-amber-700 dark:text-amber-300',
    headline: 'Review this payment',
  },
  HIGH: {
    panel: 'border-orange-200 bg-orange-50 dark:border-orange-900 dark:bg-orange-950',
    badge: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-100',
    score: 'text-orange-700 dark:text-orange-300',
    headline: 'High-risk payment',
  },
  CRITICAL: {
    panel: 'border-red-300 bg-red-50 dark:border-red-800 dark:bg-red-950',
    badge: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-100',
    score: 'text-red-700 dark:text-red-300',
    headline: 'Potential payment scam',
  },
};

/**
 * Single place for risk-level presentation. Components must use this instead
 * of hard-coding level colors/copy.
 */
export function getRiskTheme(level: RiskLevel): RiskTheme {
  return THEMES[level];
}

export function riskPanelClass(level: RiskLevel, extra?: string): string {
  return cn('rounded-2xl border p-5 shadow-sm sm:p-6', THEMES[level].panel, extra);
}
