import { useNavigate } from 'react-router-dom';
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
    <section className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-semibold">Evaluate a payment</h1>
      <p className="mt-2 text-muted-foreground">
        Enter the payment and its surrounding context. GuardPay analyzes the risk
        before any money moves — this is a simulation, no real payment happens.
      </p>
      <div className="mt-6">
        <PaymentForm onResult={handleResult} />
      </div>
    </section>
  );
}
