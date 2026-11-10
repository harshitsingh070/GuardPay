import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import CorrelationFindings from '@/components/CorrelationFindings';
import type { CorrelationFinding } from '@/types/risk';

function finding(overrides: Partial<CorrelationFinding> = {}): CorrelationFinding {
  return {
    correlationCode: 'SOCIAL_ENGINEERING_CALL_COMBO',
    category: 'SOCIAL_ENGINEERING',
    title: 'Call-guided payment to a new recipient',
    description: 'An active call, a new recipient and urgent language co-occur.',
    severity: 'HIGH',
    confidence: 0.9,
    interactionStrength: 0.82,
    contributionPoints: 18,
    participatingSignals: ['ACTIVE_CALL', 'NEW_RECIPIENT', 'URGENT_LANGUAGE'],
    evidenceFamilies: ['CALL', 'RECIPIENT', 'CONTENT'],
    triggeredConditions: ['ACTIVE_CALL', 'NEW_RECIPIENT', 'URGENT_LANGUAGE'],
    unavailableConditions: [],
    ruleVersion: '1.0',
    overlapDiscounted: false,
    explanation: 'Signals combine into a stronger fraud pattern.',
    ...overrides,
  };
}

describe('CorrelationFindings', () => {
  it('renders nothing when there are no findings', () => {
    const { container } = render(<CorrelationFindings findings={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the correlation total and each finding', () => {
    render(<CorrelationFindings findings={[finding()]} />);

    expect(screen.getByText('Signal correlation')).toBeInTheDocument();
    expect(screen.getByText('+18 risk points')).toBeInTheDocument();
    expect(screen.getByText('Call-guided payment to a new recipient')).toBeInTheDocument();
    expect(screen.getByText('HIGH')).toBeInTheDocument();
    expect(screen.getByText('+18')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE_CALL')).toBeInTheDocument();
    expect(screen.getByText('NEW_RECIPIENT')).toBeInTheDocument();
  });

  it('prefers the backend-provided contribution total', () => {
    render(
      <CorrelationFindings
        findings={[finding({ contributionPoints: 5 }), finding({ correlationCode: 'X' })]}
        contribution={9}
      />,
    );

    expect(screen.getByText('+9 risk points')).toBeInTheDocument();
  });

  it('notes when an overlap was discounted', () => {
    render(<CorrelationFindings findings={[finding({ overlapDiscounted: true })]} />);

    expect(
      screen.getByText('Overlap with a stronger correlation was discounted.'),
    ).toBeInTheDocument();
  });
});
