import { describe, expect, it } from 'vitest';
import { classifyFraudPattern } from '@/utils/fraud-pattern';

describe('classifyFraudPattern', () => {
  it('does not label an allowed payment', () => {
    expect(
      classifyFraudPattern({
        codes: ['ACTIVE_CALL', 'NEW_RECIPIENT'],
        action: 'ALLOW',
        level: 'LOW',
      }),
    ).toBeNull();
  });

  it('identifies a fake support/impersonation pattern', () => {
    const pattern = classifyFraudPattern({
      codes: ['ACTIVE_CALL', 'NEW_RECIPIENT', 'URGENT_LANGUAGE'],
      action: 'STOP',
      level: 'CRITICAL',
    });

    expect(pattern?.code).toBe('FAKE_SUPPORT');
    expect(pattern?.label).toBe('Fake PayPal/support scam');
  });

  it('identifies an invoice or payment-request scam', () => {
    const pattern = classifyFraudPattern({
      codes: ['UNEXPECTED_INVOICE', 'SUSPICIOUS_URL'],
      action: 'VERIFY',
      level: 'MEDIUM',
    });

    expect(pattern?.code).toBe('INVOICE_SCAM');
  });

  it('identifies account-takeover context', () => {
    const pattern = classifyFraudPattern({
      codes: ['NEW_DEVICE', 'NEW_GEO', 'IMPOSSIBLE_TRAVEL'],
      action: 'VERIFY',
      level: 'HIGH',
    });

    expect(pattern?.code).toBe('ACCOUNT_TAKEOVER');
  });

  it('prioritizes a reported recipient over other patterns', () => {
    const pattern = classifyFraudPattern({
      codes: ['BLOCKLISTED_RECIPIENT', 'ACTIVE_CALL', 'URGENT_LANGUAGE'],
      action: 'STOP',
      level: 'CRITICAL',
    });

    expect(pattern?.code).toBe('MONEY_MULE');
  });

  it('prioritizes first-time high-value evidence over a new recipient', () => {
    const pattern = classifyFraudPattern({
      codes: ['NEW_RECIPIENT', 'FIRST_HIGH_VALUE_TO_RECIPIENT'],
      action: 'VERIFY',
      level: 'HIGH',
    });

    expect(pattern?.code).toBe('FIRST_TIME_HIGH_VALUE');
    expect(pattern?.label).toBe('First-time high-value payment');
  });

  it('classifies from triggered findings and ignores untriggered ones', () => {
    const pattern = classifyFraudPattern({
      findings: [
        {
          code: 'REPEATED_ATTEMPTS',
          title: 'Repeated payment attempts',
          description: '',
          points: 15,
          triggered: true,
        },
        {
          code: 'BLOCKLISTED_RECIPIENT',
          title: 'Reported recipient',
          description: '',
          points: 25,
          triggered: false,
        },
      ],
      action: 'VERIFY',
      level: 'MEDIUM',
    });

    expect(pattern?.code).toBe('VELOCITY_ABUSE');
  });
});
