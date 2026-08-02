import { useEffect, useState } from 'react';
import { CircleMarker, MapContainer, Marker, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import type { Route } from '../types/routing';
import styles from './MapPinPicker.module.css';

export interface LatLng {
  lat: number;
  lng: number;
}

interface MapPinPickerProps {
  pickup: LatLng | null;
  dropoff: LatLng | null;
  onPickupChange: (coords: LatLng) => void;
  onDropoffChange: (coords: LatLng) => void;
  center?: [number, number];
  locked?: boolean;
  driverPosition?: LatLng | null;
  route?: Route | null;
}

const CASABLANCA: [number, number] = [33.5731, -7.5898];

// Leaflet writes `stroke` as an SVG presentation attribute, which any CSS rule
// outranks - so this is only a fallback and the themed colour lives in the stylesheet.
const ROUTE_STROKE = '#4bafbd';

/**
 * Leaflet takes raw markup, not components, so the markers are SVG strings.
 *
 * Built once at module scope: handing Marker a fresh icon object on every
 * render makes Leaflet tear the DOM node down and rebuild it, which kills the
 * entrance animation and flickers on any parent re-render.
 *
 * A ring for the origin and a filled square for the destination - the same
 * pairing the mode toggle already uses. Shape carries the meaning, so they stay
 * distinguishable without relying on colour.
 */
function marker(shape: string): L.DivIcon {
  return L.divIcon({
    className: styles.pin,
    html: `<svg width="22" height="22" viewBox="0 0 22 22" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">${shape}</svg>`,
    iconSize: [22, 22],
    // centred, not tip-anchored - these sit flat on the coordinate
    iconAnchor: [11, 11],
  });
}

const PICKUP_ICON = marker(`<circle cx="11" cy="11" r="7" class="${styles.pinRing}"/>`);
const DROPOFF_ICON = marker(`<rect x="3.25" y="3.25" width="15.5" height="15.5" rx="4" class="${styles.pinSquare}"/>`);

function ClickHandler({ onClick, disabled }: { onClick: (coords: LatLng) => void; disabled?: boolean }) {
  useMapEvents({
    click(e) {
      if (disabled) return;
      onClick({ lat: e.latlng.lat, lng: e.latlng.lng });
    },
  });
  return null;
}

/** Pans and zooms so the whole route is on screen - otherwise it can be drawn off-view. */
function FitRoute({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length < 2) return;
    map.fitBounds(points, { padding: [36, 36] });
  }, [map, points]);
  return null;
}

export function MapPinPicker({
  pickup,
  dropoff,
  onPickupChange,
  onDropoffChange,
  center = CASABLANCA,
  locked = false,
  driverPosition = null,
  route = null,
}: MapPinPickerProps) {
  const [mode, setMode] = useState<'pickup' | 'dropoff'>('pickup');

  function handleMapClick(coords: LatLng) {
    if (mode === 'pickup') {
      onPickupChange(coords);
      setMode('dropoff');
    } else {
      onDropoffChange(coords);
    }
  }

  const hasRoute = Boolean(route && route.points.length > 1);
  const isEstimate = route?.source === 'FALLBACK';

  return (
    <div className={styles.wrap}>
      {!locked && (
        <>
          <div className={styles.modeToggle}>
            <button
              type="button"
              className={mode === 'pickup' ? `${styles.modeButton} ${styles.modeButtonActivePickup}` : styles.modeButton}
              onClick={() => setMode('pickup')}
            >
              Set pickup
            </button>
            <button
              type="button"
              className={mode === 'dropoff' ? `${styles.modeButton} ${styles.modeButtonActiveDrop}` : styles.modeButton}
              onClick={() => setMode('dropoff')}
            >
              Set drop-off
            </button>
          </div>
          <p className={styles.hint}>Tap the map to place your {mode === 'pickup' ? 'pickup' : 'drop-off'} pin.</p>
        </>
      )}
      <div className={styles.mapContainer}>
        <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <ClickHandler onClick={handleMapClick} disabled={locked} />

          {/* Drawn before the pins so the markers stay on top of the line. Two strokes:
              a wide blurred underlay for the glow, a crisp one on top for the path.

              The `key` is load-bearing: Leaflet only applies `className` when it creates
              the path element, and react-leaflet's update path calls setStyle, which
              never touches the class. Without a keyed remount, a route that starts as a
              FALLBACK estimate and then resolves to ROAD keeps the muted styling for
              ever - it looks like the glow is broken when it is really just stale. */}
          {hasRoute && (
            <>
              <Polyline
                key={`glow-${route!.source}`}
                positions={route!.points}
                interactive={false}
                pathOptions={{
                  className: isEstimate ? styles.routeGlowFallback : styles.routeGlow,
                  color: ROUTE_STROKE,
                  weight: 16,
                  lineCap: 'round',
                  lineJoin: 'round',
                }}
              />
              <Polyline
                key={`core-${route!.source}`}
                positions={route!.points}
                interactive={false}
                pathOptions={{
                  className: isEstimate ? styles.routeCoreFallback : styles.routeCore,
                  color: ROUTE_STROKE,
                  weight: 5,
                  lineCap: 'round',
                  lineJoin: 'round',
                }}
              />
              <FitRoute points={route!.points} />
            </>
          )}

          {pickup && <Marker position={[pickup.lat, pickup.lng]} icon={PICKUP_ICON} />}
          {dropoff && <Marker position={[dropoff.lat, dropoff.lng]} icon={DROPOFF_ICON} />}
          {driverPosition && (
            <CircleMarker
              center={[driverPosition.lat, driverPosition.lng]}
              radius={8}
              pathOptions={{ color: '#ffffff', fillColor: '#09090b', fillOpacity: 1, weight: 3 }}
            />
          )}
        </MapContainer>
      </div>
    </div>
  );
}
