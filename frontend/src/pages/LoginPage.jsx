import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import Button from '../components/ui/Button'
import { TextInput } from '../components/ui/Field'
import { ErrorAlert } from '../components/ui/Feedback'
import { getErrorMessage, getFieldErrors } from '../utils/errors'

/** Seeded demo accounts, so reviewers can try every role with one click. */
const DEMO_ACCOUNTS = [
  { role: 'Admin', email: 'admin@leaveflow.com', password: 'Admin@123' },
  { role: 'Manager', email: 'tanvir.ahmed@leaveflow.com', password: 'Password@123' },
  { role: 'Employee', email: 'rakib.hasan@leaveflow.com', password: 'Password@123' },
]

export default function LoginPage() {
  const { status, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  const submit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await login(form.email, form.password)
      navigate(location.state?.from ?? '/', { replace: true })
    } catch (err) {
      setError(getErrorMessage(err))
      setFieldErrors(getFieldErrors(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-gradient-to-br from-brand-50 via-white to-slate-100 px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <img src="/favicon.svg" alt="" className="mx-auto h-12 w-12" />
          <h1 className="mt-4 text-2xl font-semibold text-slate-900">Sign in to LeaveFlow</h1>
          <p className="mt-1 text-sm text-slate-500">Plan, request and approve leave in one place</p>
        </div>

        <form onSubmit={submit} className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm" noValidate>
          <ErrorAlert message={error} />
          <TextInput id="email" label="Email" type="email" autoComplete="username" required
                     value={form.email} error={fieldErrors.email}
                     onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <TextInput id="password" label="Password" type="password" autoComplete="current-password" required
                     value={form.password} error={fieldErrors.password}
                     onChange={(e) => setForm({ ...form, password: e.target.value })} />
          <Button type="submit" loading={submitting} className="w-full">Sign in</Button>
        </form>

        <div className="mt-6 rounded-xl border border-dashed border-slate-300 bg-white/60 p-4">
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-500">Demo accounts</p>
          <div className="grid gap-2 sm:grid-cols-3">
            {DEMO_ACCOUNTS.map((account) => (
              <button key={account.role} type="button"
                      onClick={() => setForm({ email: account.email, password: account.password })}
                      className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-left text-xs hover:border-brand-500">
                <span className="block font-semibold text-slate-700">{account.role}</span>
                <span className="block truncate text-slate-400">{account.email}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
