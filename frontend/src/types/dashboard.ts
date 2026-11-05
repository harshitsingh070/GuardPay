export interface DashboardSummary {
  decisions: number;
  allowCount: number;
  verifyCount: number;
  stopCount: number;
  blockedAmount: number;
  averageScore: number;
  latency: {
    p50Ms: number;
    p95Ms: number;
    samples: number;
  };
  ai: {
    requests: number;
    available: number;
    unavailable: number;
    availabilityPct: number;
  };
  alerts: {
    total: number;
    critical: number;
    pending: number;
    sent: number;
    p95DeliveryMs: number | null;
  };
  verification: {
    pending: number;
    verified: number;
    failed: number;
    expired: number;
    successRatePct: number;
  };
}
