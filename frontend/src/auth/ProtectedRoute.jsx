import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './AuthContext'
import Spinner from '../components/ui/Spinner'

/**
 * Guards a group of routes.
 *  - Not logged in: go to /login (and come back to the requested page afterwards).
 *  - Logged in but wrong role: go to the dashboard.
 * This only hides pages; the backend still enforces every permission on each API call.
 */
export default function ProtectedRoute({ roles }) {
  const { status, user } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner label="Loading your session…" />
      </div>
    )
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
