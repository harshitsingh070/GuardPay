// Transaction history data source. The real implementation reads the Spring
// Boot API; the browser never filters or re-scores decisions itself.

import { getTransaction, listTransactions } from '@/services/api-client';
import type { TransactionHistoryService } from '@/types/transaction';

/** Optional development owner id; omitted, the backend uses the demo user. */
const configuredUserId: string | undefined =
  import.meta.env.VITE_DEMO_USER_ID ?? undefined;

export const realTransactionHistoryService: TransactionHistoryService = {
  list(userId, page, size) {
    return listTransactions(userId, page, size);
  },
  detail(id, userId) {
    return getTransaction(id, userId);
  },
};

export function getTransactionHistoryService(): TransactionHistoryService {
  return realTransactionHistoryService;
}

export function defaultUserId(): string | undefined {
  return configuredUserId;
}
