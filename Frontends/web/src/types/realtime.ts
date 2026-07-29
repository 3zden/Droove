// Notification messages relayed from Kafka via notification-service's /ws/notifications.
// The exact `payload` shape isn't fully pinned by the backend contract yet - confirm/adjust
// once notification-service's WS handler is actually built. See BRIEFING-FRONTEND.md.
export interface TripUpdateMessage {
  type: 'TRIP_UPDATE';
  payload: {
    tripId: string;
    eventType: string;
    driverId: string | null;
  };
  ts: string;
}

export interface DriverOfferMessage {
  type: 'DRIVER_OFFER';
  payload: {
    tripId: string;
    driverId: string;
    fareCents: number;
    offerId: string;
  };
  ts: string;
}

export type NotificationMessage = TripUpdateMessage | DriverOfferMessage;

// Position relayed from location-gateway's /ws/track/{driverId}.
export interface DriverPosition {
  lat: number;
  lng: number;
  heading?: number;
  ts?: string;
}
