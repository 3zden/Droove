import { useCallback, useState } from 'react';
import { login as apiLogin, register as apiRegister } from '../api/auth';
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../types/auth';

const STORAGE_KEY = 'droove.auth';

export interface Session {
  token: string;
  user: UserResponse;
}

function readStoredSession(): Session | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Session;
  } catch {
    return null;
  }
}

export function useAuth() {
  const [session, setSession] = useState<Session | null>(readStoredSession);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runAuth = useCallback(async (request: () => Promise<AuthResponse>) => {
    setLoading(true);
    setError(null);
    try {
      const result = await request();
      const next: Session = { token: result.accessToken, user: result.user };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      setSession(next);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setLoading(false);
    }
  }, []);

  const login = useCallback((credentials: LoginRequest) => runAuth(() => apiLogin(credentials)), [runAuth]);
  const register = useCallback((payload: RegisterRequest) => runAuth(() => apiRegister(payload)), [runAuth]);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setSession(null);
  }, []);

  return { session, loading, error, login, register, logout };
}
