import { Navigate } from 'react-router-dom';
import { useAuthContext } from '../context/AuthContext';

export function HomeRedirect() {
  const { session } = useAuthContext();
  return <Navigate to={session?.user.role === 'DRIVER' ? '/drive' : '/ride'} replace />;
}
