export type Role = 'RIDER' | 'DRIVER';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  role: Role;
  vehiclePlate?: string;
}

export interface UserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  vehiclePlate: string | null;
}

export interface AuthResponse {
  accessToken: string;
  user: UserResponse;
}
