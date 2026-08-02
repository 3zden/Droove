import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuthContext } from '../context/AuthContext';
import { MapPinPicker, type LatLng } from '../components/MapPinPicker';
import { Terrain } from '../components/Terrain';
import { cancelBooking, createBooking, getMyBookings } from '../api/scheduling';
import type { Booking } from '../types/scheduling';
import styles from './ScheduleHome.module.css';

function minPickupTimeLocal(): string {
  const min = new Date(Date.now() + 16 * 60 * 1000);
  min.setSeconds(0, 0);
  const offset = min.getTimezoneOffset();
  const local = new Date(min.getTime() - offset * 60 * 1000);
  return local.toISOString().slice(0, 16);
}

export function ScheduleHome() {
  const { session } = useAuthContext();
  const [pickup, setPickup] = useState<LatLng | null>(null);
  const [dropoff, setDropoff] = useState<LatLng | null>(null);
  const [pickupTime, setPickupTime] = useState('');
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const minTime = minPickupTimeLocal();

  useEffect(() => {
    if (!session) return;
    getMyBookings(session.token)
      .then(setBookings)
      .catch((err) => setError(err instanceof Error ? err.message : 'Could not load bookings'));
  }, [session]);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!session || !pickup || !dropoff || !pickupTime) {
      setError('Set a pickup, a drop-off, and a pickup time first.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const booking = await createBooking(session.token, {
        pickupTime: new Date(pickupTime).toISOString(),
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        dropLat: dropoff.lat,
        dropLng: dropoff.lng,
      });
      setBookings((prev) => [booking, ...prev]);
      setPickup(null);
      setDropoff(null);
      setPickupTime('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not schedule that ride');
    } finally {
      setLoading(false);
    }
  }

  async function handleCancel(id: string) {
    if (!session) return;
    try {
      await cancelBooking(session.token, id);
      setBookings((prev) => prev.map((b) => (b.id === id ? { ...b, status: 'CANCELLED' } : b)));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not cancel that booking');
    }
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>
        Schedule a ride, <span className={styles.titleMuted}>plan ahead</span>
      </h1>
      <p className={styles.rideLink}>
        Need a ride right now? <Link to="/ride">Request one →</Link>
      </p>

      <div className={styles.card}>
        <h2 className={styles.cardHeading}>New booking</h2>
        <form className={styles.form} onSubmit={handleSubmit}>
          <MapPinPicker pickup={pickup} dropoff={dropoff} onPickupChange={setPickup} onDropoffChange={setDropoff} />
          <div className={styles.field}>
            <label className={styles.label} htmlFor="pickup-time">
              Pickup time
            </label>
            <input
              id="pickup-time"
              type="datetime-local"
              required
              min={minTime}
              className={styles.input}
              value={pickupTime}
              onChange={(e) => setPickupTime(e.target.value)}
            />
          </div>
          {error && <p className={styles.error}>{error}</p>}
          <button type="submit" className={styles.button} disabled={loading}>
            {loading ? 'Scheduling...' : 'Schedule ride →'}
          </button>
        </form>
      </div>

      <div className={styles.card}>
        <h2 className={styles.cardHeading}>Your bookings</h2>
        {bookings.length === 0 && (
          <div className={styles.emptyState}>
            <p className={styles.empty}>No upcoming bookings yet.</p>
            <Terrain variant="mist" className={styles.emptyTerrain} />
          </div>
        )}
        {bookings.length > 0 && (
          <ul className={styles.bookingList}>
            {bookings.map((booking) => (
              <li key={booking.id} className={styles.bookingRow}>
                <span>
                  <span className={styles.bookingTime}>{new Date(booking.pickupTime).toLocaleString()}</span>
                  <br />
                  <span className={styles.bookingStatus}>{booking.status}</span>
                </span>
                {booking.status === 'SCHEDULED' && (
                  <button type="button" className={styles.buttonSmall} onClick={() => handleCancel(booking.id)}>
                    Cancel
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
