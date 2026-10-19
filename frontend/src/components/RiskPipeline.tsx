import type { GuardedPaymentResponse } from '@/types/payment';

export interface PipelineContext {
  amount: string;
  currency: string;
  recipient: string;
  merchant: string;
}

function Stage({
  index,
  title,
  children,
}: {
  index: number;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div className="rounded-lg border bg-card p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Stage {index} · {title}
        </p>
        <div className="mt-2 text-sm">{children}</div>
      </div>
      <p className="py-1 text-center text-muted-foreground" aria-hidden="true">
        ↓
      </p>
    </div>
  );
}

/**
 * Judge-facing pipeline. Every value comes from the backend response (plus
 * the submitted context the user typed) — nothing is computed or invented
 * here. In particular the score is displayed, never re-derived.
 */
export default function RiskPipeline({
  result,
  context,
}: {
  result: GuardedPaymentResponse;
  context: PipelineContext;
}) {
  const triggered = result.risk.findings.filter((finding) => finding.triggered);
  const aiSignals = result.risk.automaticSignals.filter(
    (signal) => signal.source === 'AI' && signal.detected,
  );
  const orderCreated = result.paypalOrder !== null;

  return (
    <div aria-label="GuardPay evaluation pipeline">
      <Stage index={1} title="Transaction">
        <p className="font-medium">
          {context.amount === '' ? '—' : `$${context.amount}`} {context.currency}
        </p>
        <p className="text-muted-foreground">
          Recipient: {context.recipient === '' ? '—' : context.recipient} · Merchant:{' '}
          {context.merchant === '' ? '—' : context.merchant}
        </p>
      </Stage>

      <Stage index={2} title="Automatic signals">
        {triggered.length === 0 ? (
          <p className="text-muted-foreground">No signals triggered.</p>
        ) : (
          <ul className="space-y-1">
            {triggered.map((finding) => (
              <li key={finding.code}>
                {finding.title} <span className="font-semibold">+{finding.points}</span>
              </li>
            ))}
          </ul>
        )}
      </Stage>

      <Stage index={3} title="AI evidence">
        {aiSignals.length === 0 ? (
          <p className="text-muted-foreground">
            No AI findings accepted
            {result.risk.aiStatus === 'UNAVAILABLE'
              ? ' (AI analysis was unavailable).'
              : '.'}
          </p>
        ) : (
          <ul className="space-y-2">
            {aiSignals.map((signal) => (
              <li key={signal.type} className="rounded border p-2">
                <p className="font-medium">
                  {signal.type}
                  {signal.severity !== null && signal.severity !== undefined ? (
                    <span className="ml-2 text-xs font-semibold text-muted-foreground">
                      {signal.severity}
                      {signal.confidence !== null && signal.confidence !== undefined
                        ? ` · Confidence: ${Math.round(signal.confidence * 100)}%`
                        : ''}
                    </span>
                  ) : null}
                </p>
                <p className="text-muted-foreground">{signal.evidence}</p>
              </li>
            ))}
          </ul>
        )}
        <p className="mt-2 text-xs text-muted-foreground">
          AI provides evidence. GuardPay&apos;s deterministic Risk Engine makes the payment
          decision.
        </p>
      </Stage>

      <Stage index={4} title="Risk engine">
        <p className="text-2xl font-bold tabular-nums">
          {result.risk.score} <span className="text-base font-medium">/ 100</span>
        </p>
        <p className="font-medium">{result.risk.riskLevel} — GuardPay Risk Score</p>
      </Stage>

      <Stage index={5} title="Decision">
        {result.risk.recommendedAction === 'STOP' && (
          <p className="font-semibold">🛑 PAYMENT BLOCKED — GuardPay stopped this payment before a PayPal order was created.</p>
        )}
        {result.risk.recommendedAction === 'VERIFY' && (
          <p className="font-semibold">⚠️ Additional verification required — no PayPal order was created.</p>
        )}
        {result.risk.recommendedAction === 'ALLOW' && (
          <p className="font-semibold">✅ Payment appears safe — continue to PayPal.</p>
        )}
      </Stage>

      <div className="rounded-lg border bg-card p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Stage 6 · PayPal
        </p>
        <div className="mt-2 text-sm">
          {orderCreated ? (
            <p>
              Order created:{' '}
              <span className="font-mono text-xs">{result.paypalOrder?.orderId}</span>
            </p>
          ) : (
            <p className="font-medium">
              Order NOT CREATED
              <span className="ml-2 font-mono text-xs text-muted-foreground">
                (PayPal Order ID: None)
              </span>
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
