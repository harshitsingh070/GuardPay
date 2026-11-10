import type { RecommendedAction, RiskFinding, RiskLevel } from '@/types/risk';

export interface FraudPattern {
  code: string;
  label: string;
  description: string;
}

interface ClassificationInput {
  codes?: string[];
  findings?: RiskFinding[];
  action?: RecommendedAction | null;
  level?: RiskLevel | null;
}

const WARNING_LEVELS = new Set<RiskLevel>(['MEDIUM', 'HIGH', 'CRITICAL']);

function hasAny(codes: Set<string>, values: string[]): boolean {
  return values.some((value) => codes.has(value));
}

/**
 * Presentation-only classification of the backend's triggered findings.
 * It does not score the payment; the Spring Boot engine remains authoritative
 * for the risk level and ALLOW / VERIFY / STOP decision.
 */
export function classifyFraudPattern({
  codes,
  findings,
  action,
  level,
}: ClassificationInput): FraudPattern | null {
  const isWarning =
    action === 'VERIFY' ||
    action === 'STOP' ||
    (level !== null && level !== undefined && WARNING_LEVELS.has(level));
  if (!isWarning) return null;

  const triggered = new Set(
    codes ?? findings?.filter((finding) => finding.triggered).map((finding) => finding.code) ?? [],
  );
  const accountTakeover = ['NEW_DEVICE', 'NEW_GEO', 'IMPOSSIBLE_TRAVEL', 'VPN_PROXY'];
  const socialEngineering = ['SOCIAL_ENGINEERING', 'URGENT_LANGUAGE', 'SUSPICIOUS_URL'];
  const velocity = ['REPEATED_ATTEMPTS', 'VELOCITY_HOURLY', 'VELOCITY_DAILY'];
  const firstTimeHighValue = [
    'FIRST_HIGH_VALUE_PAYMENT',
    'FIRST_HIGH_VALUE_TO_RECIPIENT',
    'FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE',
  ];

  if (triggered.has('BLOCKLISTED_RECIPIENT')) {
    return {
      code: 'MONEY_MULE',
      label: 'Reported recipient / money-mule risk',
      description:
        'This recipient is on your fraud blocklist. Scammers often route stolen funds through money-mule accounts.',
    };
  }

  if (
    triggered.has('ACTIVE_CALL') &&
    hasAny(triggered, [
      ...socialEngineering,
      'UNEXPECTED_INVOICE',
      'NEW_RECIPIENT',
      'UNKNOWN_MERCHANT',
    ])
  ) {
    return {
      code: 'FAKE_SUPPORT',
      label: 'Fake PayPal/support scam',
      description:
        'The pattern resembles a caller or message impersonating PayPal support while pressuring you to pay a new recipient.',
    };
  }

  if (triggered.has('UNEXPECTED_INVOICE')) {
    return {
      code: 'INVOICE_SCAM',
      label: 'Invoice or payment-request scam',
      description:
        'An unexpected invoice or payment request is combined with other warning signs, such as pressure or a suspicious link.',
    };
  }

  if (hasAny(triggered, socialEngineering)) {
    return {
      code: 'SOCIAL_ENGINEERING',
      label: 'Social-engineering scam',
      description:
        'The payment context contains urgency, pressure, impersonation, or a suspicious link typical of a scam.',
    };
  }

  if (hasAny(triggered, accountTakeover)) {
    return {
      code: 'ACCOUNT_TAKEOVER',
      label: 'Possible account takeover',
      description:
        'The payment came from an unfamiliar device, location, or network, which can mean someone else is using the account.',
    };
  }

  if (hasAny(triggered, velocity)) {
    return {
      code: 'VELOCITY_ABUSE',
      label: 'Rapid payment abuse',
      description:
        'Several payment attempts in a short window can indicate coercion, card testing, or an automated attack.',
    };
  }

  if (hasAny(triggered, firstTimeHighValue)) {
    return {
      code: 'FIRST_TIME_HIGH_VALUE',
      label: 'First-time high-value payment',
      description:
        'This amount is far above your own established payment history. GuardPay is treating it as an unverified first-time high-value payment, especially when the recipient is new.',
    };
  }

  if (triggered.has('NEW_RECIPIENT')) {
    return {
      code: 'NEW_RECIPIENT',
      label: 'Unverified new-recipient payment',
      description:
        'This is a first-time payment to this recipient, so GuardPay cannot rely on prior trusted history.',
    };
  }

  if (triggered.has('MISSING_CONTEXT')) {
    return {
      code: 'UNVERIFIED_PAYMENT',
      label: 'Unverified high-value payment',
      description:
        'A high-value payment was requested without the message, link, or trusted-device context GuardPay expects.',
    };
  }

  return {
    code: 'SUSPICIOUS_PAYMENT',
    label: 'Suspicious payment pattern',
    description:
      'Multiple risk signals combined to cross GuardPay’s warning threshold. Review the evidence before continuing.',
  };
}
