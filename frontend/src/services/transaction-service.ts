// Transaction history data source. The real implementation reads the Spring
// Boot API; the browser never filters or re-scores decisions itself.
// The fixed demo workspace is resolved server-side.

import { getTransaction, listTransactions } from '@/services/api-client';
import type { TransactionHistoryService } from '@/types/transaction';

export const realTransactionHistoryService: TransactionHistoryService = {
  list(page, size) {
    return listTransactions(page, size);
  },
  detail(id) {
    return getTransaction(id);
  },
};

export function getTransactionHistoryService(): TransactionHistoryService {
  return realTransactionHistoryService;
}
