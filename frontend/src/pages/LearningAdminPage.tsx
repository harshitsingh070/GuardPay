import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  BrainCircuit,
  Database,
  Loader2,
  Play,
  RefreshCw,
  ShieldCheck,
  Undo2,
} from 'lucide-react';
import { usePageTitle } from '@/hooks/usePageTitle';
import {
  ApiRequestError,
  approveLearningModel,
  buildLearningDataset,
  deployLearningModel,
  getLearningOverview,
  listLearningDatasets,
  listLearningEvents,
  listLearningModels,
  rejectLearningModel,
  rollbackLearningModel,
  trainLearningModel,
} from '@/services/api-client';
import type {
  LearningDataset,
  LearningEvent,
  LearningModel,
  LearningOverview,
  TrainingResult,
} from '@/types/intelligence';

function messageOf(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message;
  return 'The learning console could not be loaded. Admin access is required.';
}

function pct(value: number | null): string {
  return value === null ? '—' : `${(value * 100).toFixed(1)}%`;
}

/** Admin-only continuous-learning console: datasets, candidates, deployment. */
export default function LearningAdminPage() {
  usePageTitle('Learning console');
  const [overview, setOverview] = useState<LearningOverview | null>(null);
  const [datasets, setDatasets] = useState<LearningDataset[]>([]);
  const [models, setModels] = useState<LearningModel[]>([]);
  const [events, setEvents] = useState<LearningEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextOverview, nextDatasets, nextModels, nextEvents] = await Promise.all([
        getLearningOverview(),
        listLearningDatasets(),
        listLearningModels(),
        listLearningEvents(),
      ]);
      setOverview(nextOverview);
      setDatasets(nextDatasets);
      setModels(nextModels);
      setEvents(nextEvents);
    } catch (failure) {
      setError(messageOf(failure));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(key: string, action: () => Promise<unknown>, success: string) {
    setBusy(key);
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(success);
      await load();
    } catch (failure) {
      setError(messageOf(failure));
    } finally {
      setBusy(null);
    }
  }

  async function build() {
    setBusy('build');
    setError(null);
    setNotice(null);
    try {
      const dataset = await buildLearningDataset();
      setNotice(
        dataset.unmetRequirements.length === 0
          ? `Dataset ${dataset.datasetVersion} built with ${dataset.recordCount} verified records.`
          : `Dataset ${dataset.datasetVersion}: ${dataset.unmetRequirements.join('; ')}`,
      );
      await load();
    } catch (failure) {
      setError(messageOf(failure));
    } finally {
      setBusy(null);
    }
  }

  async function train(dataset: LearningDataset) {
    setBusy(`train-${dataset.checksum}`);
    setError(null);
    setNotice(null);
    try {
      const result: TrainingResult = await trainLearningModel(dataset.id);
      setNotice(
        result.trained
          ? `Candidate ${result.modelVersion} trained (verify ${result.verifyThreshold}, stop ${result.stopThreshold}, F1 ${pct(result.f1)}).`
          : `Training refused: ${result.failureReason}`,
      );
      await load();
    } catch (failure) {
      setError(messageOf(failure));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3.5">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[#eaf2ff] text-guardpay-blue">
            <BrainCircuit className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <p className="gp-eyebrow">Admin</p>
            <h1 className="mt-0.5 text-2xl font-bold tracking-tight text-guardpay-ink sm:text-3xl">
              Continuous learning
            </h1>
            <p className="mt-1 max-w-2xl text-sm text-guardpay-muted">
              Datasets are built only from admin-verified labels. A candidate can change decisions
              only after explicit approval and deployment; rollback restores the previous champion.
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => void build()}
            disabled={busy !== null}
            className="inline-flex items-center gap-2 rounded-full bg-guardpay-blue px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
          >
            {busy === 'build' ? (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <Database className="h-4 w-4" aria-hidden="true" />
            )}
            Build dataset
          </button>
          <button
            type="button"
            onClick={() => void load()}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-full border border-guardpay-line bg-white px-4 py-2.5 text-sm font-semibold text-guardpay-ink"
          >
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Refresh
          </button>
        </div>
      </div>

      {loading && (
        <div className="gp-card mt-6 flex items-center gap-3 p-6" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-guardpay-blue" aria-hidden="true" />
          <span className="text-sm text-guardpay-muted">Loading learning state…</span>
        </div>
      )}

      {!loading && error !== null && (
        <div className="mt-6 rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-800" role="alert">
          <p className="flex items-center gap-2 font-semibold">
            <AlertTriangle className="h-4 w-4" aria-hidden="true" />
            Learning request failed
          </p>
          <p className="mt-1">{error}</p>
        </div>
      )}

      {notice !== null && (
        <div className="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900" role="status">
          {notice}
        </div>
      )}

      {!loading && overview !== null && (
        <div className="gp-card mt-6 grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
              Active calibration
            </p>
            <p className="mt-1 flex items-center gap-2 text-sm font-bold text-guardpay-ink">
              <ShieldCheck className="h-4 w-4 text-emerald-700" aria-hidden="true" />
              {overview.activeCalibration.deployed ? 'Deployed model' : 'Deterministic defaults'}
            </p>
            <p className="mt-1 text-xs text-guardpay-muted">
              verify ≥ {overview.activeCalibration.verifyThreshold} · stop ≥{' '}
              {overview.activeCalibration.stopThreshold}
            </p>
            <p className="text-xs text-guardpay-muted">{overview.activeCalibration.modelVersion}</p>
          </div>
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
              Datasets
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-guardpay-ink">
              {overview.totalDatasets}
            </p>
            <p className="text-xs text-guardpay-muted">
              {overview.readyDatasets} ready · {overview.insufficientDatasets} insufficient ·{' '}
              {overview.invalidatedDatasets} invalidated
            </p>
          </div>
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
              Candidates
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-guardpay-ink">
              {overview.candidateModels}
            </p>
            <p className="text-xs text-guardpay-muted">{overview.totalModels} models recorded</p>
          </div>
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
              Active models
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-guardpay-ink">
              {overview.activeModels}
            </p>
            <p className="text-xs text-guardpay-muted">One champion at most</p>
          </div>
        </div>
      )}

      {!loading && (
        <div className="mt-6 grid gap-6 xl:grid-cols-2">
          <div className="gp-card overflow-hidden">
            <div className="border-b border-guardpay-line px-4 py-3.5">
              <h2 className="text-sm font-bold text-guardpay-ink">Datasets</h2>
              <p className="text-xs text-guardpay-muted">
                Chronological 70/15/15 split · SHA-256 addressed · immutable.
              </p>
            </div>
            <div className="divide-y divide-guardpay-line">
              {datasets.length === 0 && (
                <p className="px-4 py-8 text-center text-sm text-guardpay-muted">
                  No datasets yet. Build one once verified labels exist.
                </p>
              )}
              {datasets.map((dataset) => (
                <div key={dataset.datasetVersion} className="px-4 py-3.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-mono text-xs font-semibold text-guardpay-ink">
                      {dataset.datasetVersion}
                    </p>
                    <span className="rounded-full bg-guardpay-canvas px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                      {dataset.status.replace(/_/g, ' ')}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-guardpay-muted">
                    {dataset.recordCount} records ({dataset.fraudCount} fraud /{' '}
                    {dataset.legitimateCount} legitimate) · train {dataset.trainCount} / val{' '}
                    {dataset.validationCount} / test {dataset.testCount}
                    {dataset.excludedCount > 0 ? ` · ${dataset.excludedCount} excluded` : ''}
                  </p>
                  {dataset.unmetRequirements.length > 0 && (
                    <p className="mt-1 text-xs text-amber-800">
                      {dataset.unmetRequirements.join('; ')}
                    </p>
                  )}
                  <div className="mt-2 flex items-center gap-2">
                    <button
                      type="button"
                      disabled={busy !== null || dataset.status !== 'READY'}
                      onClick={() => void train(dataset)}
                      className="inline-flex items-center gap-1.5 rounded-full bg-guardpay-navy px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                    >
                      {busy !== null && busy.startsWith('train') ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                      ) : (
                        <Play className="h-3.5 w-3.5" aria-hidden="true" />
                      )}
                      Train candidate
                    </button>
                    <span className="font-mono text-[10px] text-guardpay-muted">
                      {dataset.checksum.slice(0, 12)}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="gp-card overflow-hidden">
            <div className="border-b border-guardpay-line px-4 py-3.5">
              <h2 className="text-sm font-bold text-guardpay-ink">Models</h2>
              <p className="text-xs text-guardpay-muted">
                Inspectable threshold artifacts. Deployment is always explicit.
              </p>
            </div>
            <div className="divide-y divide-guardpay-line">
              {models.length === 0 && (
                <p className="px-4 py-8 text-center text-sm text-guardpay-muted">
                  No candidates trained yet.
                </p>
              )}
              {models.map((model) => (
                <div key={model.modelVersion} className="px-4 py-3.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-mono text-xs font-semibold text-guardpay-ink">
                      {model.modelVersion}
                    </p>
                    <span className="rounded-full bg-guardpay-canvas px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-guardpay-muted">
                      {model.status}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-guardpay-muted">
                    verify {model.verifyThreshold ?? '—'} / stop {model.stopThreshold ?? '—'} ·
                    precision {pct(model.precision)} · recall {pct(model.recall)} · F1{' '}
                    {pct(model.f1)} · test {model.testCount}
                  </p>
                  {model.trainingFailureReason !== null && (
                    <p className="mt-1 text-xs text-amber-800">{model.trainingFailureReason}</p>
                  )}
                  <div className="mt-2 flex flex-wrap gap-2">
                    {model.status === 'CANDIDATE' && (
                      <>
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() =>
                            void run(
                              `approve-${model.modelVersion}`,
                              () => approveLearningModel(model.id),
                              `Approved ${model.modelVersion}.`,
                            )
                          }
                          className="rounded-full bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                        >
                          Approve
                        </button>
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() =>
                            void run(
                              `reject-${model.modelVersion}`,
                              () => rejectLearningModel(model.id),
                              `Rejected ${model.modelVersion}.`,
                            )
                          }
                          className="rounded-full border border-guardpay-line px-3 py-1.5 text-xs font-semibold text-guardpay-ink disabled:opacity-50"
                        >
                          Reject
                        </button>
                      </>
                    )}
                    {model.status === 'APPROVED' && (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() =>
                          void run(
                            `deploy-${model.modelVersion}`,
                            () => deployLearningModel(model.id),
                            `Deployed ${model.modelVersion}.`,
                          )
                        }
                        className="rounded-full bg-guardpay-blue px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                      >
                        Deploy champion
                      </button>
                    )}
                    {model.status === 'ACTIVE' && (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() =>
                          void run(
                            `rollback-${model.modelVersion}`,
                            () => rollbackLearningModel(model.id),
                            `Rolled back ${model.modelVersion}.`,
                          )
                        }
                        className="inline-flex items-center gap-1.5 rounded-full border border-guardpay-line px-3 py-1.5 text-xs font-semibold text-guardpay-ink disabled:opacity-50"
                      >
                        <Undo2 className="h-3.5 w-3.5" aria-hidden="true" />
                        Rollback
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {!loading && events.length > 0 && (
        <div className="gp-card mt-6 overflow-hidden">
          <div className="border-b border-guardpay-line px-4 py-3.5">
            <h2 className="text-sm font-bold text-guardpay-ink">Learning audit trail</h2>
          </div>
          <ul className="divide-y divide-guardpay-line">
            {events.slice(0, 20).map((event) => (
              <li key={`${event.eventType}-${event.occurredAt}`} className="px-4 py-2.5 text-xs">
                <span className="font-semibold text-guardpay-ink">{event.eventType}</span>
                <span className="ml-2 text-guardpay-muted">{event.detail}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
