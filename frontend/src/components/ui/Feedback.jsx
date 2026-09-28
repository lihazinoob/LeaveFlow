import { AlertCircle, Inbox } from 'lucide-react'
import Spinner from './Spinner'

/** A red box for an error message returned by the API. */
export function ErrorAlert({ message, className = '' }) {
  if (!message) return null
  return (
    <div className={`flex items-start gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 ${className}`} role="alert">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <span>{message}</span>
    </div>
  )
}

export function EmptyState({ title, description, icon: Icon = Inbox, action }) {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-10 text-center">
      <Icon className="mb-3 h-8 w-8 text-slate-300" />
      <p className="text-sm font-medium text-slate-600">{title}</p>
      {description && <p className="mt-1 text-sm text-slate-400">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

/** Shows a spinner, an error or the children, the three states of any loaded section. */
export function AsyncContent({ loading, error, data, children }) {
  if (error) return <ErrorAlert message={error} />
  if (loading && !data) return <Spinner label="Loading…" className="py-6" />
  return children
}

export function PageHeader({ title, description, actions }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}
