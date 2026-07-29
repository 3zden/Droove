import { AuthPage } from './pages/AuthPage';
import { useAuth } from './hooks/useAuth';

function App() {
  const { session, loading, error, login, register, logout } = useAuth();

  if (!session) {
    return <AuthPage loading={loading} error={error} onLogin={login} onRegister={register} />;
  }

  return (
    <div style={{ padding: 32 }}>
      <p>
        Signed in as {session.user.firstName} {session.user.lastName} ({session.user.role})
      </p>
      <button type="button" onClick={logout}>
        Log out
      </button>
    </div>
  );
}

export default App;
