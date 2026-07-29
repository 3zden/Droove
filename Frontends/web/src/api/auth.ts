import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth';

export const API_URL = import.meta.env.VITE_USER_API_URL ?? 'http://localhost:8101/api/users';

// EXERCISE (see BRIEFING-AUTH.md): wire this up to `POST ${API_URL}/login`.
export async function login(_credentials: LoginRequest): Promise<AuthResponse> {
  throw new Error('login() is not wired up yet - see BRIEFING-AUTH.md');
}

// EXERCISE (see BRIEFING-AUTH.md): wire this up to `POST ${API_URL}/register`.
export async function register(_payload: RegisterRequest): Promise<AuthResponse> {
  throw new Error('register() is not wired up yet - see BRIEFING-AUTH.md');
}
