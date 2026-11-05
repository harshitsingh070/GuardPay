import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { apiBaseUrl } from '@/services/api-client';
import type { Alert } from '@/types/alerts';

export type AlertConnection = 'connecting' | 'live' | 'reconnecting' | 'unsupported';

interface AlertsContextValue {
  alerts: Alert[];
  connection: AlertConnection;
  latest: Alert | null;
  clearLatest: () => void;
}

const AlertsContext = createContext<AlertsContextValue | null>(null);

const MAX_ALERTS = 20;

function parseAlert(event: Event): Alert | null {
  try {
    return JSON.parse((event as MessageEvent<string>).data) as Alert;
  } catch {
    return null;
  }
}

/**
 * One shared SSE connection for the whole app. The snapshot seeds the feed on
 * connect; only genuinely new `alert` events raise a toast.
 */
export function AlertsProvider({ children }: { children: ReactNode }) {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [connection, setConnection] = useState<AlertConnection>('connecting');
  const [latest, setLatest] = useState<Alert | null>(null);

  useEffect(() => {
    if (typeof EventSource === 'undefined') {
      setConnection('unsupported');
      return;
    }
    const source = new EventSource(`${apiBaseUrl}/api/alerts/stream`, {
      withCredentials: true,
    });

    const onConnected = () => setConnection('live');
    const onSnapshot = (event: Event) => {
      const parsed = parseAlert(event);
      if (parsed === null) return;
      const snapshot = parsed as unknown as Alert[];
      setAlerts(snapshot.slice(0, MAX_ALERTS));
      setConnection('live');
    };
    const onAlert = (event: Event) => {
      const alert = parseAlert(event);
      if (alert === null) return;
      setAlerts((previous) => [alert, ...previous.filter((row) => row.id !== alert.id)].slice(0, MAX_ALERTS));
      setLatest(alert);
      setConnection('live');
    };
    const onError = () => setConnection('reconnecting');

    source.addEventListener('connected', onConnected);
    source.addEventListener('snapshot', onSnapshot);
    source.addEventListener('alert', onAlert);
    source.addEventListener('error', onError);

    return () => {
      source.close();
    };
  }, []);

  const clearLatest = useCallback(() => setLatest(null), []);

  const value = useMemo(
    () => ({ alerts, connection, latest, clearLatest }),
    [alerts, connection, latest, clearLatest],
  );

  return <AlertsContext.Provider value={value}>{children}</AlertsContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAlerts(): AlertsContextValue {
  const context = useContext(AlertsContext);
  if (context === null) {
    throw new Error('useAlerts must be used inside AlertsProvider');
  }
  return context;
}
