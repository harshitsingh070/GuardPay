import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import FirstTimeHighValueCard from '@/components/FirstTimeHighValueCard';
import type { RiskFinding } from '@/types/risk';

function finding(overrides: Partial<RiskFinding> = {}): RiskFinding {
  return {
    code: 'FIRST_HIGH_VALUE_PAYMENT',
    title: 'First high-value payment',
    description: 'This amount is far above your established payment history.',
    points: 22,
    triggered: true,
    severity: 'HIGH',
    confidence: 0.82,
    deviationRatio: 6.4,
    observedValue: 12000,
    expectedValue: 1875,
    profileWindow: '90d',
    ...overrides,
  };
}

describe('FirstTimeHighValueCard', () => {
  it('renders nothing when no high-value finding is triggered', () => {
    const { container } = render(
      <FirstTimeHighValueCard
        findings={[finding({ code: 'NEW_RECIPIENT', triggered: true })]}
      />,
    );

    expect(container.firstChild).toBeNull();
  });

  it('shows the backend evidence and metrics without re-scoring', () => {
    render(<FirstTimeHighValueCard findings={[finding()]} />);

    expect(screen.getByText('First high-value payment')).toBeInTheDocument();
    expect(screen.getByText('HIGH')).toBeInTheDocument();
    expect(screen.getByText('6.40×')).toBeInTheDocument();
    expect(screen.getByText('12,000')).toBeInTheDocument();
    expect(screen.getByText('1,875')).toBeInTheDocument();
    expect(screen.getByText('90d')).toBeInTheDocument();
    expect(screen.getByText('82%')).toBeInTheDocument();
    expect(screen.getByText('+22')).toBeInTheDocument();
  });

  it('ignores untriggered high-value findings', () => {
    const { container } = render(
      <FirstTimeHighValueCard findings={[finding({ triggered: false })]} />,
    );

    expect(container.firstChild).toBeNull();
  });
});
