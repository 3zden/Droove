import { useState, type FormEvent } from 'react';
import { ThemeToggle } from '../components/ThemeToggle';
import styles from './AuthPage.module.css';
import type { LoginRequest, RegisterRequest, Role } from '../types/auth';

type Mode = 'login' | 'register';

interface AuthPageProps {
  loading: boolean;
  error: string | null;
  onLogin: (credentials: LoginRequest) => void;
  onRegister: (payload: RegisterRequest) => void;
}

function PasswordField({ id, label, autoComplete }: { id: string; label: string; autoComplete: string }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      <div className={styles.inputWrap}>
        <input
          id={id}
          name="password"
          type={visible ? 'text' : 'password'}
          required
          autoComplete={autoComplete}
          className={styles.input}
        />
        <button
          type="button"
          className={styles.passwordToggle}
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
        >
          {visible ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a21.6 21.6 0 0 1 5.06-6.06M9.9 4.24A10.94 10.94 0 0 1 12 4c7 0 11 8 11 8a21.6 21.6 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
              <line x1="1" y1="1" x2="23" y2="23" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          )}
        </button>
      </div>
    </div>
  );
}

export function AuthPage({ loading, error, onLogin, onRegister }: AuthPageProps) {
  const [mode, setMode] = useState<Mode>('login');
  const [role, setRole] = useState<Role>('RIDER');

  function handleLogin(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    onLogin({
      email: String(data.get('email')),
      password: String(data.get('password')),
    });
  }

  function handleRegister(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const vehiclePlate = data.get('vehiclePlate');
    onRegister({
      firstName: String(data.get('firstName')),
      lastName: String(data.get('lastName')),
      email: String(data.get('email')),
      password: String(data.get('password')),
      role,
      ...(role === 'DRIVER' && vehiclePlate ? { vehiclePlate: String(vehiclePlate) } : {}),
    });
  }

  return (
    <div className={styles.page}>
      <div className={styles.themeToggleWrap}>
        <ThemeToggle />
      </div>

      <section className={styles.brandPanel}>
        <span className={styles.brandMark}>DROOVE</span>
        <h1 className={styles.brandHeadline}>
          Move the city <span className={styles.brandHeadlineMuted}>with you.</span>
        </h1>
        <p className={styles.brandBody}>Riders get a quote in seconds. Drivers pick up in minutes.</p>
        <div className={styles.brandStats}>
          <span className={styles.stat}>
            <span className={styles.statValue}>Live</span>
            <span className={styles.statLabel}>driver tracking</span>
          </span>
          <span className={styles.stat}>
            <span className={styles.statValue}>Upfront</span>
            <span className={styles.statLabel}>pricing</span>
          </span>
        </div>
      </section>

      <section className={styles.formPanel}>
        <div className={styles.card}>
          <div className={styles.tabs} role="tablist" aria-label="Auth mode">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'login'}
              className={mode === 'login' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
              onClick={() => setMode('login')}
            >
              Log in
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'register'}
              className={mode === 'register' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
              onClick={() => setMode('register')}
            >
              Sign up
            </button>
          </div>

          {mode === 'login' ? (
            <>
              <h2 className={styles.heading}>Welcome back</h2>
              <p className={styles.subheading}>Log in to request or drive a ride.</p>
              <form className={styles.form} onSubmit={handleLogin}>
                <div className={styles.field}>
                  <label className={styles.label} htmlFor="login-email">
                    Email
                  </label>
                  <input id="login-email" name="email" type="email" required autoComplete="email" className={styles.input} />
                </div>
                <PasswordField id="login-password" label="Password" autoComplete="current-password" />
                {error && (
                  <p className={styles.error} role="alert">
                    {error}
                  </p>
                )}
                <button type="submit" className={styles.submit} disabled={loading}>
                  {loading ? 'Logging in...' : (
                    <>
                      Log in <span aria-hidden="true">&rarr;</span>
                    </>
                  )}
                </button>
              </form>
            </>
          ) : (
            <>
              <h2 className={styles.heading}>Create your account</h2>
              <p className={styles.subheading}>Sign up as a rider or a driver.</p>
              <form className={styles.form} onSubmit={handleRegister}>
                <div className={styles.roleGroup} role="radiogroup" aria-label="Account type">
                  {(['RIDER', 'DRIVER'] as const).map((r) => (
                    <div className={styles.rolePill} key={r}>
                      <input
                        id={`role-${r}`}
                        className={styles.roleInput}
                        type="radio"
                        name="role"
                        value={r}
                        checked={role === r}
                        onChange={() => setRole(r)}
                      />
                      <label className={styles.roleLabel} htmlFor={`role-${r}`}>
                        {r === 'RIDER' ? 'Rider' : 'Driver'}
                      </label>
                    </div>
                  ))}
                </div>

                <div className={styles.row}>
                  <div className={styles.field}>
                    <label className={styles.label} htmlFor="register-firstName">
                      First name
                    </label>
                    <input
                      id="register-firstName"
                      name="firstName"
                      type="text"
                      required
                      autoComplete="given-name"
                      className={styles.input}
                    />
                  </div>
                  <div className={styles.field}>
                    <label className={styles.label} htmlFor="register-lastName">
                      Last name
                    </label>
                    <input
                      id="register-lastName"
                      name="lastName"
                      type="text"
                      required
                      autoComplete="family-name"
                      className={styles.input}
                    />
                  </div>
                </div>

                <div className={styles.field}>
                  <label className={styles.label} htmlFor="register-email">
                    Email
                  </label>
                  <input id="register-email" name="email" type="email" required autoComplete="email" className={styles.input} />
                </div>

                <PasswordField id="register-password" label="Password" autoComplete="new-password" />

                {role === 'DRIVER' && (
                  <div className={styles.field}>
                    <label className={styles.label} htmlFor="register-vehiclePlate">
                      Vehicle plate
                    </label>
                    <input
                      id="register-vehiclePlate"
                      name="vehiclePlate"
                      type="text"
                      required
                      autoComplete="off"
                      className={styles.input}
                    />
                  </div>
                )}

                {error && (
                  <p className={styles.error} role="alert">
                    {error}
                  </p>
                )}
                <button type="submit" className={styles.submit} disabled={loading}>
                  {loading ? 'Creating account...' : (
                    <>
                      Create account <span aria-hidden="true">&rarr;</span>
                    </>
                  )}
                </button>
              </form>
            </>
          )}
        </div>
      </section>
    </div>
  );
}
