import type { ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuthContext } from '../context/AuthContext';
import { ThemeToggle } from './ThemeToggle';
import styles from './AppShell.module.css';

function RideIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 17h14M6.5 17V9.5l1.8-3.6A1.5 1.5 0 0 1 9.6 5h4.8a1.5 1.5 0 0 1 1.3.9L17.5 9.5V17" />
      <circle cx="8.5" cy="14" r="1" />
      <circle cx="15.5" cy="14" r="1" />
      <path d="M7 20v-3M17 20v-3" />
    </svg>
  );
}

function ScheduleIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3.5" y="5" width="17" height="15" rx="3" />
      <path d="M3.5 10h17M8 3v4M16 3v4" />
    </svg>
  );
}

function WalletIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3.5 8.5A2.5 2.5 0 0 1 6 6h11a2.5 2.5 0 0 1 2.5 2.5v8A2.5 2.5 0 0 1 17 19H6a2.5 2.5 0 0 1-2.5-2.5Z" />
      <path d="M3.5 10h17M16 14.5h.01" />
    </svg>
  );
}

function DriveIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3.5v5M12 15.5v5M3.5 12h5M15.5 12h5" />
    </svg>
  );
}

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
}

const RIDER_LINKS: NavItem[] = [
  { to: '/ride', label: 'Ride', icon: <RideIcon /> },
  { to: '/schedule', label: 'Schedule', icon: <ScheduleIcon /> },
  { to: '/wallet', label: 'Wallet', icon: <WalletIcon /> },
];

const DRIVER_LINKS: NavItem[] = [
  { to: '/drive', label: 'Drive', icon: <DriveIcon /> },
  { to: '/wallet', label: 'Wallet', icon: <WalletIcon /> },
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
          {/* Desktop only - phones get the bottom tab bar instead. */}
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

      <nav className={styles.tabBar} aria-label="Main">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) => (isActive ? `${styles.tab} ${styles.tabActive}` : styles.tab)}
          >
            {link.icon}
            <span>{link.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
