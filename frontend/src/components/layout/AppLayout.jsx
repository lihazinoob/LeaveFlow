import { Suspense, useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import {
  CalendarDays, CalendarRange, ClipboardCheck, LayoutDashboard, LogOut, Menu, PartyPopper, UserCircle, Users, X,
} from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import { RoleBadge } from '../ui/Badge'
import Spinner from '../ui/Spinner'

/** Sidebar links. `roles` = who sees the link (all roles when omitted). */
const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/leaves', label: 'My Leaves', icon: CalendarDays },
  { to: '/approvals', label: 'Approvals', icon: ClipboardCheck, roles: ['MANAGER', 'ADMIN'] },
  { to: '/calendar', label: 'Team Calendar', icon: CalendarRange, roles: ['MANAGER', 'ADMIN'] },
  { to: '/employees', label: 'Employees', icon: Users, roles: ['ADMIN'] },
  { to: '/holidays', label: 'Holidays', icon: PartyPopper },
  { to: '/profile', label: 'My Profile', icon: UserCircle },
]

export default function AppLayout() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const items = NAV_ITEMS.filter((item) => !item.roles || item.roles.includes(user.role))

  const sidebar = (
    <nav className="flex h-full flex-col bg-slate-900 text-slate-300">
      <div className="flex items-center gap-2 px-5 py-5">
        <img src="/favicon.svg" alt="" className="h-8 w-8" />
        <span className="text-lg font-semibold text-white">LeaveFlow</span>
      </div>
      <ul className="flex-1 space-y-1 px-3">
        {items.map(({ to, label, icon: Icon, end }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors
                ${isActive ? 'bg-brand-600 text-white' : 'hover:bg-slate-800 hover:text-white'}`}
            >
              <Icon className="h-4 w-4" />
              {label}
            </NavLink>
          </li>
        ))}
      </ul>
      <div className="border-t border-slate-800 p-4">
        <p className="truncate text-sm font-medium text-white">{user.fullName}</p>
        <p className="mb-2 truncate text-xs text-slate-400">{user.designation}</p>
        <div className="flex items-center justify-between">
          <RoleBadge role={user.role} />
          <button type="button" onClick={logout}
                  className="flex items-center gap-1.5 rounded-md px-2 py-1 text-xs text-slate-400 hover:bg-slate-800 hover:text-white">
            <LogOut className="h-3.5 w-3.5" /> Log out
          </button>
        </div>
      </div>
    </nav>
  )

  return (
    <div className="flex h-full">
      <aside className="hidden w-60 shrink-0 lg:block">{sidebar}</aside>

      {menuOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setMenuOpen(false)} />
          <aside className="relative h-full w-64">{sidebar}</aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-3 border-b border-slate-200 bg-white px-4 py-3 lg:hidden">
          <button type="button" onClick={() => setMenuOpen(!menuOpen)} className="rounded-md p-1 text-slate-600"
                  aria-label="Toggle menu">
            {menuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
          </button>
          <span className="font-semibold text-slate-800">LeaveFlow</span>
        </header>
        <main className="flex-1 overflow-y-auto">
          <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
            {/* Pages are lazy-loaded; only the content area waits, the sidebar stays. */}
            <Suspense fallback={<Spinner label="Loading…" className="py-8" />}>
              <Outlet />
            </Suspense>
          </div>
        </main>
      </div>
    </div>
  )
}
