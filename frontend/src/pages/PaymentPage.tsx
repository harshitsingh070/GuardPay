import { useNavigate } from 'react-router-dom';
import { FlaskConical } from 'lucide-react';
import PaymentForm from '@/components/PaymentForm';
import { usePageTitle } from '@/hooks/usePageTitle';
import type { RiskEvaluationRequest, RiskEvaluationResponse } from '@/types/risk';

export default function PaymentPage() {
  usePageTitle('Payment');
  const navigate = useNavigate();

  function handleResult(result: RiskEvaluationResponse, request: RiskEvaluationRequest) {
    // Pass via navigation state — never in the URL.
    navigate('/risk', { state: { result, request } });
  }

  return (
    <section className="mx-auto max-w-3xl">
      <div className="flex items-start gap-3.5">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[#eaf2ff] text-guardpay-blue">
          <FlaskConical className="h-5 w-5" aria-hidden="true" />
        </span>
        <div>
          <p className="gp-eyebrow">Manual demo</p>
          <h1 className="mt-0.5 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
            Evaluate a payment
          </h1>
          <p className="mt-1 max-w-xl text-sm text-guardpay-muted">
            Enter the payment and its surrounding context. GuardPay analyzes the risk before any
            money moves — this is a simulation, no real payment happens.
          </p>
        </div>
      </div>

      <div className="mt-6">
        <PaymentForm onResult={handleResult} />
      </div>
    </section>
  );
}
