import { Navigate, Outlet } from 'react-router-dom';
import { useAuthContext } from '../context/AuthContext';

export function RequireAuth() {
  const { session } = useAuthContext();
  if (!session) return <Navigate to="/auth" replace />;
  return <Outlet />;
}
