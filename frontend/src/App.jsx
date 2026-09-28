import { lazy } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import { ToastProvider } from './components/ui/Toast'
import AppLayout from './components/layout/AppLayout'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'

// Pages are loaded on demand (code splitting): the login screen does not download the chart library, for example.
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const MyLeavesPage = lazy(() => import('./pages/MyLeavesPage'))
const ApprovalsPage = lazy(() => import('./pages/ApprovalsPage'))
const CalendarPage = lazy(() => import('./pages/CalendarPage'))
const EmployeesPage = lazy(() => import('./pages/EmployeesPage'))
const HolidaysPage = lazy(() => import('./pages/HolidaysPage'))
const ProfilePage = lazy(() => import('./pages/ProfilePage'))

/**
 * Route map. Nested <ProtectedRoute> elements add login and role checks;
 * <AppLayout> renders the sidebar around every logged-in page.
 */
export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route index element={<DashboardPage />} />
                <Route path="leaves" element={<MyLeavesPage />} />
                <Route path="holidays" element={<HolidaysPage />} />
                <Route path="profile" element={<ProfilePage />} />
                <Route element={<ProtectedRoute roles={['MANAGER', 'ADMIN']} />}>
                  <Route path="approvals" element={<ApprovalsPage />} />
                  <Route path="calendar" element={<CalendarPage />} />
                </Route>
                <Route element={<ProtectedRoute roles={['ADMIN']} />}>
                  <Route path="employees" element={<EmployeesPage />} />
                </Route>
              </Route>
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
