const STATUS_STYLES = {
  PENDING: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  REJECTED: 'bg-red-50 text-red-700 ring-red-600/20',
  CANCELLED: 'bg-slate-100 text-slate-600 ring-slate-500/20',
}

const ROLE_STYLES = {
  ADMIN: 'bg-purple-50 text-purple-700 ring-purple-600/20',
  MANAGER: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  EMPLOYEE: 'bg-slate-100 text-slate-600 ring-slate-500/20',
}

const BASE = 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset'

export function StatusBadge({ status }) {
  return <span className={`${BASE} ${STATUS_STYLES[status]}`}>{status.charAt(0) + status.slice(1).toLowerCase()}</span>
}

export function RoleBadge({ role }) {
  return <span className={`${BASE} ${ROLE_STYLES[role]}`}>{role.charAt(0) + role.slice(1).toLowerCase()}</span>
}

/** A leave type label with its colour dot, e.g. "● Annual Leave". */
export function LeaveTypeTag({ name, color }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-sm">
      <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: color }} />
      {name}
    </span>
  )
}
