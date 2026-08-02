import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CircleMarker, MapContainer, TileLayer } from 'react-leaflet';
import { useAuthContext } from '../context/AuthContext';
import { Terrain } from '../components/Terrain';
import { connectSocket, LOCATION_WS_URL, NOTIFICATION_WS_URL, type SocketHandle } from '../api/socket';
import { acceptOffer, declineOffer } from '../api/matching';
import { completeTrip, markArrived, startTrip } from '../api/trips';
import type { DriverOfferMessage, NotificationMessage } from '../types/realtime';
import styles from './DriveHome.module.css';

const CASABLANCA = { lat: 33.5731, lng: -7.5898 };
const OFFER_WINDOW_SECONDS = 15;

type DriveStatus = 'MATCHED' | 'DRIVER_ARRIVED' | 'IN_PROGRESS' | 'COMPLETED';

function nextSimPosition(prev: { lat: number; lng: number }) {
  const deltaLat = (Math.random() - 0.5) * 0.002;
  const deltaLng = (Math.random() - 0.5) * 0.002;
  const heading = (Math.atan2(deltaLng, deltaLat) * 180) / Math.PI;
  return { lat: prev.lat + deltaLat, lng: prev.lng + deltaLng, heading };
}

export function DriveHome() {
  const { session } = useAuthContext();
  const [searchParams] = useSearchParams();
  const simMode = searchParams.get('sim') === '1';

  const [online, setOnline] = useState(false);
  const [position, setPosition] = useState(CASABLANCA);
  const [offer, setOffer] = useState<DriverOfferMessage['payload'] | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(OFFER_WINDOW_SECONDS);
  const [activeTrip, setActiveTrip] = useState<{ id: string; status: DriveStatus } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const locationSocketRef = useRef<SocketHandle | null>(null);
  const notificationSocketRef = useRef<SocketHandle | null>(null);
  const watchIdRef = useRef<number | null>(null);
  const simIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!session || !online) return;

    notificationSocketRef.current = connectSocket(`${NOTIFICATION_WS_URL}/ws/notifications?token=${session.token}`, (data) => {
      const message = data as NotificationMessage;
      if (message.type === 'DRIVER_OFFER') {
        setOffer(message.payload);
        setSecondsLeft(OFFER_WINDOW_SECONDS);
      }
    });

    locationSocketRef.current = connectSocket(`${LOCATION_WS_URL}/ws/location?token=${session.token}`, () => {});

    if (simMode) {
      simIntervalRef.current = setInterval(() => {
        setPosition((prev) => {
          const next = nextSimPosition(prev);
          locationSocketRef.current?.send({ lat: next.lat, lng: next.lng, heading: next.heading, ts: new Date().toISOString() });
          return next;
        });
      }, 2000);
    } else if ('geolocation' in navigator) {
      watchIdRef.current = navigator.geolocation.watchPosition((geo) => {
        const next = { lat: geo.coords.latitude, lng: geo.coords.longitude };
        setPosition(next);
        locationSocketRef.current?.send({
          lat: next.lat,
          lng: next.lng,
          heading: geo.coords.heading ?? 0,
          ts: new Date().toISOString(),
        });
      });
    }

    return () => {
      locationSocketRef.current?.close();
      notificationSocketRef.current?.close();
      if (simIntervalRef.current) clearInterval(simIntervalRef.current);
      if (watchIdRef.current !== null) navigator.geolocation.clearWatch(watchIdRef.current);
    };
  }, [session, online, simMode]);

  useEffect(() => {
    if (!offer) return;
    if (secondsLeft <= 0) {
      handleDeclineOffer();
      return;
    }
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [offer, secondsLeft]);

  async function handleAcceptOffer() {
    if (!session || !offer) return;
    try {
      await acceptOffer(session.token, offer.offerId);
      setActiveTrip({ id: offer.tripId, status: 'MATCHED' });
      setOffer(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not accept the offer');
    }
  }

  async function handleDeclineOffer() {
    if (!session || !offer) return;
    try {
      await declineOffer(session.token, offer.offerId);
    } catch {
      // offer already expired server-side is fine - nothing to surface here.
    }
    setOffer(null);
  }

  async function advanceTrip(next: DriveStatus) {
    if (!session || !activeTrip) return;
    try {
      if (next === 'DRIVER_ARRIVED') await markArrived(session.token, activeTrip.id);
      if (next === 'IN_PROGRESS') await startTrip(session.token, activeTrip.id);
      if (next === 'COMPLETED') await completeTrip(session.token, activeTrip.id);
      setActiveTrip({ id: activeTrip.id, status: next });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update the trip');
    }
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>
        Drive, <span className={styles.titleMuted}>earn on your schedule</span>
      </h1>

      <div className={styles.card}>
        <div className={styles.toggleRow}>
          <div>
            <p className={styles.toggleLabel}>
              {online && <span className="live-dot" aria-hidden="true" />}
              {online ? 'Online' : 'Offline'}
            </p>
            <p className={styles.toggleSub}>
              {online ? 'Streaming your position, waiting for offers.' : 'Go online to start receiving ride offers.'}
              {simMode ? ' (simulated route)' : ''}
            </p>
          </div>
          <button
            type="button"
            className={online ? `${styles.switch} ${styles.switchOn}` : styles.switch}
            role="switch"
            aria-checked={online}
            onClick={() => setOnline((o) => !o)}
          >
            <span className={styles.switchKnob} />
          </button>
        </div>

        {online ? (
          <div className={styles.mapContainer}>
            <MapContainer center={[position.lat, position.lng]} zoom={14} style={{ height: '100%', width: '100%' }}>
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <CircleMarker center={[position.lat, position.lng]} radius={9} pathOptions={{ color: '#4bafbd', fillColor: '#4bafbd', fillOpacity: 0.9 }} />
            </MapContainer>
          </div>
        ) : (
          <div className={styles.restPanel}>
            <Terrain variant="route" />
          </div>
        )}

        {error && <p className={styles.error}>{error}</p>}
      </div>

      {activeTrip && (
        <div className={styles.card}>
          <p className={styles.statusText}>Trip {activeTrip.status.replace('_', ' ').toLowerCase()}</p>
          <div className={styles.buttonRow}>
            {activeTrip.status === 'MATCHED' && (
              <button type="button" className={styles.button} onClick={() => advanceTrip('DRIVER_ARRIVED')}>
                Arrived →
              </button>
            )}
            {activeTrip.status === 'DRIVER_ARRIVED' && (
              <button type="button" className={styles.button} onClick={() => advanceTrip('IN_PROGRESS')}>
                Start →
              </button>
            )}
            {activeTrip.status === 'IN_PROGRESS' && (
              <button type="button" className={styles.button} onClick={() => advanceTrip('COMPLETED')}>
                Complete →
              </button>
            )}
            {activeTrip.status === 'COMPLETED' && (
              <button type="button" className={styles.buttonSecondary} onClick={() => setActiveTrip(null)}>
                Done
              </button>
            )}
          </div>
        </div>
      )}

      {offer && (
        <div className={styles.overlay}>
          <div className={styles.offerCard}>
            <p className={styles.offerCountdown}>{secondsLeft}s to respond</p>
            <p className={styles.offerFare}>{(offer.fareCents / 100).toFixed(2)} MAD</p>
            <p className={styles.offerSub}>New ride request nearby</p>
            <div className={styles.offerButtons}>
              <button type="button" className={styles.buttonSecondary} onClick={handleDeclineOffer}>
                Decline
              </button>
              <button type="button" className={styles.button} onClick={handleAcceptOffer}>
                Accept →
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
