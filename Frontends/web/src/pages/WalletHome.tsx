import { useState, type FormEvent } from 'react';
import { useAuthContext } from '../context/AuthContext';
import { getTripLedger, getWallet, topUp } from '../api/payments';
import type { LedgerTransaction } from '../types/payments';
import styles from './WalletHome.module.css';

const currency = new Intl.NumberFormat('en-MA', { style: 'currency', currency: 'MAD' });

function formatCents(cents: number): string {
  return currency.format(cents / 100);
}

export function WalletHome() {
  const { session } = useAuthContext();
  const [balanceCents, setBalanceCents] = useState<number | null>(null);
  const [topupAmount, setTopupAmount] = useState('');
  const [tripId, setTripId] = useState('');
  const [ledger, setLedger] = useState<LedgerTransaction[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadBalance() {
    if (!session) return;
    try {
      const wallet = await getWallet(session.token);
      setBalanceCents(wallet.balanceCents);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load wallet');
    }
  }

  async function handleTopup(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!session) return;
    const amountCents = Math.round(Number(topupAmount) * 100);
    if (!Number.isFinite(amountCents) || amountCents <= 0) {
      setError('Enter a positive top-up amount');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const wallet = await topUp(session.token, amountCents);
      setBalanceCents(wallet.balanceCents);
      setTopupAmount('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Top-up failed');
    } finally {
      setLoading(false);
    }
  }

  async function handleLookupLedger(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!session || !tripId.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const rows = await getTripLedger(session.token, tripId.trim());
      setLedger(rows);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load ledger for that trip');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Wallet</h1>

      <div className={styles.card}>
        <p className={styles.balanceLabel}>Balance</p>
        <p className={styles.balanceValue}>{balanceCents === null ? '—' : formatCents(balanceCents)}</p>
        <button type="button" className={styles.button} onClick={loadBalance} style={{ marginTop: 16 }}>
          Refresh
        </button>
      </div>

      <div className={styles.card}>
        <h2 className={styles.cardHeading}>Top up</h2>
        <form className={styles.form} onSubmit={handleTopup}>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="topup-amount">
              Amount (MAD)
            </label>
            <input
              id="topup-amount"
              type="number"
              min="1"
              step="0.01"
              required
              className={styles.input}
              value={topupAmount}
              onChange={(e) => setTopupAmount(e.target.value)}
            />
          </div>
          <button type="submit" className={styles.button} disabled={loading}>
            {loading ? 'Adding...' : 'Add funds →'}
          </button>
        </form>
      </div>

      <div className={styles.card}>
        <h2 className={styles.cardHeading}>Trip receipt</h2>
        <form className={styles.form} onSubmit={handleLookupLedger}>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="trip-id">
              Trip ID
            </label>
            <input
              id="trip-id"
              type="text"
              required
              className={styles.input}
              value={tripId}
              onChange={(e) => setTripId(e.target.value)}
            />
          </div>
          <button type="submit" className={styles.button} disabled={loading}>
            Look up →
          </button>
        </form>

        {ledger && ledger.length === 0 && <p className={styles.empty}>No ledger entries for that trip yet.</p>}
        {ledger && ledger.length > 0 && (
          <ul className={styles.ledgerList}>
            {ledger.map((tx) => (
              <li key={tx.id} className={styles.ledgerRow}>
                <span>
                  <span className={styles.ledgerType}>{tx.type}</span>
                  <br />
                  <span className={styles.ledgerDate}>{new Date(tx.createdAt).toLocaleString()}</span>
                </span>
                <span className={styles.ledgerAmount}>{formatCents(tx.amountCents)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {error && <p className={styles.error}>{error}</p>}
    </div>
  );
}
