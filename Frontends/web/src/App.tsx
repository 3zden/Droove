import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { AuthRoute } from './pages/AuthRoute';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './components/RequireAuth';
import { HomeRedirect } from './components/HomeRedirect';
import { RideHome } from './pages/RideHome';
import { ScheduleHome } from './pages/ScheduleHome';
import { WalletHome } from './pages/WalletHome';
import { DriveHome } from './pages/DriveHome';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/auth" element={<AuthRoute />} />
          <Route element={<RequireAuth />}>
            <Route element={<AppShell />}>
              <Route path="/" element={<HomeRedirect />} />
              <Route path="/ride" element={<RideHome />} />
              <Route path="/schedule" element={<ScheduleHome />} />
              <Route path="/wallet" element={<WalletHome />} />
              <Route path="/drive" element={<DriveHome />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/auth" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
