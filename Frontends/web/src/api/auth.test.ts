import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AuthResponse, RegisterRequest } from '../types/auth';
import { API_URL, login, register } from './auth';

const mockAuthResponse: AuthResponse = {
  accessToken: 'jwt.test.token',
  user: {
    id: 'a3f1b2c0-0000-4000-8000-000000000001',
    email: 'nadia@droove.io',
    firstName: 'Nadia',
    lastName: 'Haddad',
    role: 'RIDER',
    vehiclePlate: null,
  },
};

function mockFetchOnce(status: number, body: unknown) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }) as unknown as typeof fetch;
}

describe('auth api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('login() POSTs JSON credentials and returns the parsed AuthResponse', async () => {
    mockFetchOnce(200, mockAuthResponse);

    const result = await login({ email: 'nadia@droove.io', password: 'hunter2' });

    expect(fetch).toHaveBeenCalledWith(
      `${API_URL}/login`,
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ email: 'nadia@droove.io', password: 'hunter2' }),
      }),
    );
    expect(result).toEqual(mockAuthResponse);
  });

  it('login() rejects with a readable error when the backend answers 401', async () => {
    mockFetchOnce(401, { message: 'Invalid credentials' });

    await expect(login({ email: 'nadia@droove.io', password: 'wrong' })).rejects.toThrow();
  });

  it('register() POSTs the full payload and returns the parsed AuthResponse', async () => {
    mockFetchOnce(201, mockAuthResponse);
    const payload: RegisterRequest = {
      firstName: 'Nadia',
      lastName: 'Haddad',
      email: 'nadia@droove.io',
      password: 'hunter2',
      role: 'RIDER',
    };

    const result = await register(payload);

    expect(fetch).toHaveBeenCalledWith(
      `${API_URL}/register`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    );
    expect(result).toEqual(mockAuthResponse);
  });
});
