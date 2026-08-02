import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthContext } from '../context/AuthContext';
import { MapPinPicker, type LatLng } from '../components/MapPinPicker';
import { cancelTrip, createTrip, getTrip } from '../api/trips';
import { getQuote } from '../api/pricing';
import { getRoute } from '../api/routing';
import { getTripLedger } from '../api/payments';
import { connectSocket, LOCATION_WS_URL, NOTIFICATION_WS_URL } from '../api/socket';
import type { Quote } from '../types/pricing';
import type { Route } from '../types/routing';
import type { Trip } from '../types/trips';
import type { LedgerTransaction } from '../types/payments';
import type { DriverPosition, NotificationMessage } from '../types/realtime';
import styles from './RideHome.module.css';

const currency = new Intl.NumberFormat('en-MA', { style: 'currency', currency: 'MAD' });

const STATUS_COPY: Record<Trip['status'], { text: string; sub: string }> = {
  REQUESTED: { text: 'Looking for a driver', sub: 'This usually takes a few seconds.' },
  MATCHED: { text: 'Driver is on the way', sub: 'Watch their position update live.' },
  DRIVER_ARRIVED: { text: 'Your driver has arrived', sub: 'Head out when you’re ready.' },
  IN_PROGRESS: { text: 'Ride in progress', sub: 'Sit back, you’re on your way.' },
  COMPLETED: { text: 'Trip completed', sub: 'Here’s your receipt.' },
  CANCELLED: { text: 'Trip cancelled', sub: 'Request a new ride whenever you’re ready.' },
  NO_DRIVERS_FOUND: { text: 'No drivers available', sub: 'Try again in a moment.' },
};

const ACTIVE_STATUSES: Trip['status'][] = ['REQUESTED', 'MATCHED', 'DRIVER_ARRIVED', 'IN_PROGRESS'];
const CANCELLABLE_STATUSES: Trip['status'][] = ['REQUESTED', 'MATCHED', 'DRIVER_ARRIVED'];

const STEPS: { status: Trip['status']; label: string }[] = [
  { status: 'REQUESTED', label: 'Requested' },
  { status: 'MATCHED', label: 'Matched' },
  { status: 'DRIVER_ARRIVED', label: 'Arrived' },
  { status: 'IN_PROGRESS', label: 'In progress' },
  { status: 'COMPLETED', label: 'Completed' },
];

function TripStepper({ status }: { status: Trip['status'] }) {
  const activeIndex = STEPS.findIndex((step) => step.status === status);
  return (
    <div className={styles.stepper}>
      {STEPS.map((step, i) => (
        <div
          key={step.status}
          className={styles.step}
          data-state={i < activeIndex ? 'done' : i === activeIndex ? 'active' : 'pending'}
        >
          <span className={styles.stepDot} />
          <span className={styles.stepLabel}>{step.label}</span>
        </div>
      ))}
    </div>
  );
}

export function RideHome() {
  const { session } = useAuthContext();
  const [pickup, setPickup] = useState<LatLng | null>(null);
  const [dropoff, setDropoff] = useState<LatLng | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [route, setRoute] = useState<Route | null>(null);
  const [trip, setTrip] = useState<Trip | null>(null);
  const [driverPosition, setDriverPosition] = useState<DriverPosition | null>(null);
  const [receipt, setReceipt] = useState<LedgerTransaction[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Live quote and drawn route the moment both pins are set, before any trip exists.
  //
  // Fired independently, not with Promise.all, on purpose: the price is the thing the
  // rider is waiting for, and routing-service being down must not take it with it. The
  // map just loses its line.
  useEffect(() => {
    if (!pickup || !dropoff || trip) return;
    let cancelled = false;

    getQuote({ pickupLat: pickup.lat, pickupLng: pickup.lng, dropLat: dropoff.lat, dropLng: dropoff.lng })
      .then((q) => {
        if (!cancelled) setQuote(q);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Could not get a quote');
      });

    getRoute({ fromLat: pickup.lat, fromLng: pickup.lng, toLat: dropoff.lat, toLng: dropoff.lng })
      .then((r) => {
        if (!cancelled) setRoute(r);
      })
      .catch(() => {
        if (!cancelled) setRoute(null);
      });

    return () => {
      cancelled = true;
    };
  }, [pickup, dropoff, trip]);

  // Listen for trip status changes over /ws/notifications while a trip is active.
  useEffect(() => {
    if (!session || !trip || !ACTIVE_STATUSES.includes(trip.status)) return;

    const url = `${NOTIFICATION_WS_URL}/ws/notifications?token=${session.token}`;
    const socket = connectSocket(url, (data) => {
      const message = data as NotificationMessage;
      if (message.type === 'TRIP_UPDATE' && message.payload.tripId === trip.id) {
        getTrip(session.token, trip.id)
          .then(setTrip)
          .catch((err) => setError(err instanceof Error ? err.message : 'Could not refresh trip status'));
      }
    });
    return () => socket.close();
  }, [session, trip]);

  // Once matched, track the driver's live position over /ws/track/{driverId}.
  useEffect(() => {
    if (!session || !trip || trip.status !== 'MATCHED' || !trip.driverId) return;

    const url = `${LOCATION_WS_URL}/ws/track/${trip.driverId}?token=${session.token}`;
    const socket = connectSocket(url, (data) => {
      setDriverPosition(data as DriverPosition);
    });
    return () => socket.close();
  }, [session, trip]);

  // Once completed, fetch the receipt.
  useEffect(() => {
    if (!session || !trip || trip.status !== 'COMPLETED') return;
    getTripLedger(session.token, trip.id)
      .then(setReceipt)
      .catch((err) => setError(err instanceof Error ? err.message : 'Could not load the receipt'));
  }, [session, trip]);

  async function handleRequestRide() {
    if (!session || !pickup || !dropoff) return;
    setLoading(true);
    setError(null);
    try {
      const newTrip = await createTrip(session.token, {
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        dropLat: dropoff.lat,
        dropLng: dropoff.lng,
      });
      setTrip(newTrip);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not request a ride');
    } finally {
      setLoading(false);
    }
  }

  async function handleCancel() {
    if (!session || !trip) return;
    try {
      const updated = await cancelTrip(session.token, trip.id);
      setTrip(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not cancel the trip');
    }
  }

  function handleNewRide() {
    setTrip(null);
    setQuote(null);
    setRoute(null);
    setPickup(null);
    setDropoff(null);
    setDriverPosition(null);
    setReceipt(null);
  }

  const isActiveTrip = Boolean(trip) && ACTIVE_STATUSES.includes(trip!.status);

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>
        Request a ride, <span className={styles.titleMuted}>move in minutes</span>
      </h1>

      {!trip && (
        <p className={styles.scheduleLink}>
          Need this for later? <Link to="/schedule">Schedule a ride →</Link>
        </p>
      )}

      {isActiveTrip && <TripStepper status={trip!.status} />}

      <div className={styles.card}>
        <MapPinPicker
          pickup={pickup}
          dropoff={dropoff}
          onPickupChange={setPickup}
          onDropoffChange={setDropoff}
          locked={Boolean(trip)}
          driverPosition={driverPosition}
          route={route}
        />

        {quote && !trip && (
          <div className={styles.quoteRow}>
            <div className={styles.quoteStat}>
              <span className={styles.quoteLabel}>Fare</span>
              <span className={styles.quoteValue}>{currency.format(quote.fareCents / 100)}</span>
            </div>
            <div className={styles.quoteStat}>
              <span className={styles.quoteLabel}>Distance</span>
              <span className={styles.quoteValue}>{quote.distanceKm.toFixed(1)} km</span>
            </div>
            <div className={styles.quoteStat}>
              <span className={styles.quoteLabel}>ETA</span>
              <span className={styles.quoteValue}>{Math.round(quote.durationMin)} min</span>
            </div>
            {quote.surge > 1 && (
              <div className={styles.quoteStat}>
                <span className={styles.quoteLabel}>Surge</span>
                <span className={styles.quoteValue}>{quote.surge.toFixed(1)}x</span>
              </div>
            )}
          </div>
        )}

        {/* The route is drawn either way - this says which kind of line you're looking at. */}
        {route && !trip && (
          <p className={styles.routeNote} data-source={route.source}>
            {route.source === 'ROAD'
              ? `Optimal road route · ${(route.distanceMeters / 1000).toFixed(1)} km`
              : 'Estimated route — live road routing is unavailable right now.'}
          </p>
        )}

        {!trip && (
          <button type="button" className={styles.button} onClick={handleRequestRide} disabled={!pickup || !dropoff || loading}>
            {loading ? 'Requesting...' : 'Request Droove →'}
          </button>
        )}

        {error && <p className={styles.error}>{error}</p>}

        {isActiveTrip && (
          <div className={styles.floatingStatus}>
            <div>
              <p className={styles.statusText}>{STATUS_COPY[trip!.status].text}</p>
              <p className={styles.statusSub}>{STATUS_COPY[trip!.status].sub}</p>
            </div>
            {CANCELLABLE_STATUSES.includes(trip!.status) && (
              <button type="button" className={styles.buttonDanger} onClick={handleCancel}>
                Cancel
              </button>
            )}
          </div>
        )}
      </div>

      {trip && !isActiveTrip && (
        <div className={styles.card}>
          <div className={styles.statusPanel}>
            <div>
              <p className={styles.statusText}>{STATUS_COPY[trip.status].text}</p>
              <p className={styles.statusSub}>{STATUS_COPY[trip.status].sub}</p>
            </div>
            <button type="button" className={styles.button} onClick={handleNewRide}>
              New ride →
            </button>
          </div>

          {trip.status === 'COMPLETED' && (
            <div>
              <div className={styles.receiptRow}>
                <span>Fare</span>
                <span>{currency.format(trip.fareCents / 100)}</span>
              </div>
              {receipt?.map((tx) => (
                <div key={tx.id} className={styles.receiptRow}>
                  <span>{tx.type}</span>
                  <span>{currency.format(tx.amountCents / 100)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
