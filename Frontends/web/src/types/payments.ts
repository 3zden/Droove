export interface WalletBalance {
  balanceCents: number;
}

export type LedgerTxType = 'ESCROW_HOLD' | 'DISBURSE' | 'REFUND' | 'TOPUP';

export interface LedgerTransaction {
  id: string;
  type: LedgerTxType;
  tripId: string | null;
  createdAt: string;
  amountCents: number;
}
