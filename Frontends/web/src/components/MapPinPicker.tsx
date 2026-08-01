import { useState } from 'react';
import { CircleMarker, MapContainer, TileLayer, useMapEvents } from 'react-leaflet';
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
}

const CASABLANCA: [number, number] = [33.5731, -7.5898];

function ClickHandler({ onClick, disabled }: { onClick: (coords: LatLng) => void; disabled?: boolean }) {
  useMapEvents({
    click(e) {
      if (disabled) return;
      onClick({ lat: e.latlng.lat, lng: e.latlng.lng });
    },
  });
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
          {pickup && (
            <CircleMarker center={[pickup.lat, pickup.lng]} radius={9} pathOptions={{ color: '#4bafbd', fillColor: '#4bafbd', fillOpacity: 0.9 }} />
          )}
          {dropoff && (
            <CircleMarker center={[dropoff.lat, dropoff.lng]} radius={9} pathOptions={{ color: '#f0796b', fillColor: '#f0796b', fillOpacity: 0.9 }} />
          )}
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
