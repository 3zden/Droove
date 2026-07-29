import { Navigate } from 'react-router-dom';
import { AuthPage } from './AuthPage';
import { useAuthContext } from '../context/AuthContext';

export function AuthRoute() {
  const { session, loading, error, login, register } = useAuthContext();

  if (session) {
    return <Navigate to={session.user.role === 'DRIVER' ? '/drive' : '/ride'} replace />;
  }

  return <AuthPage loading={loading} error={error} onLogin={login} onRegister={register} />;
}
