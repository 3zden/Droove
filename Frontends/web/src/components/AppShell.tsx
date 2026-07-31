import { NavLink, Outlet } from 'react-router-dom';
import { useAuthContext } from '../context/AuthContext';
import { ThemeToggle } from './ThemeToggle';
import styles from './AppShell.module.css';

const RIDER_LINKS = [
  { to: '/ride', label: 'Ride' },
  { to: '/schedule', label: 'Schedule' },
  { to: '/wallet', label: 'Wallet' },
];

const DRIVER_LINKS = [
  { to: '/drive', label: 'Drive' },
  { to: '/wallet', label: 'Wallet' },
];

export function AppShell() {
  const { session, logout } = useAuthContext();
  const links = session?.user.role === 'DRIVER' ? DRIVER_LINKS : RIDER_LINKS;
  const homeLink = session?.user.role === 'DRIVER' ? '/drive' : '/ride';

  return (
    <div className={styles.shell}>
      <nav className={styles.nav}>
        <div className={styles.navLeft}>
          <NavLink to={homeLink} className={styles.brand}>
            DROOVE
          </NavLink>
          <div className={styles.links}>
            {links.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) => (isActive ? `${styles.link} ${styles.linkActive}` : styles.link)}
              >
                {link.label}
              </NavLink>
            ))}
          </div>
        </div>
        <div className={styles.navRight}>
          {session && <span className={styles.userName}>{session.user.firstName}</span>}
          <ThemeToggle />
          <button type="button" className={styles.logout} onClick={logout}>
            Log out
          </button>
        </div>
      </nav>
      <div className={styles.content}>
        <Outlet />
      </div>
    </div>
  );
}
