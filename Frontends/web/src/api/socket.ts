export const LOCATION_WS_URL = import.meta.env.VITE_LOCATION_WS_URL ?? 'ws://localhost:8201';
export const NOTIFICATION_WS_URL = import.meta.env.VITE_NOTIFICATION_WS_URL ?? 'ws://localhost:8202';

export interface SocketHandle {
  send: (data: unknown) => void;
  close: () => void;
}

// EXERCISE (see BRIEFING-FRONTEND.md): open `new WebSocket(url)`, call `onMessage(JSON.parse(event.data))`
// for each message, and wire `send` to `socket.send(JSON.stringify(data))`. `close()` should close the
// socket. Used for /ws/notifications (receive-only), /ws/track/{driverId} (receive-only), and
// /ws/location (send-only, driver pings) - same wiring, different URL and direction.
export function connectSocket(_url: string, _onMessage: (data: unknown) => void): SocketHandle {
  console.warn('connectSocket() is not wired up yet - see BRIEFING-FRONTEND.md');
  return { send: () => {}, close: () => {} };
}
