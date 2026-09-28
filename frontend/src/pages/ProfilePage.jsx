import { useState } from 'react'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import { RoleBadge } from '../components/ui/Badge'
import { TextInput } from '../components/ui/Field'
import { AsyncContent, ErrorAlert, PageHeader } from '../components/ui/Feedback'
import { useToast } from '../components/ui/Toast'
import useAsync from '../hooks/useAsync'
import { authApi } from '../api/endpoints'
import { getErrorMessage, getFieldErrors } from '../utils/errors'
import { formatDate } from '../utils/format'

const EMPTY_FORM = { currentPassword: '', newPassword: '', confirmPassword: '' }

export default function ProfilePage() {
  const profile = useAsync(() => authApi.me(), [])
  const me = profile.data

  return (
    <>
      <PageHeader title="My profile" />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Details">
          <AsyncContent {...profile}>
            {me && (
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <Detail label="Name">{me.fullName}</Detail>
                <Detail label="Employee code">{me.employeeCode}</Detail>
                <Detail label="Email">{me.email}</Detail>
                <Detail label="Role"><RoleBadge role={me.role} /></Detail>
                <Detail label="Department">{me.department}</Detail>
                <Detail label="Designation">{me.designation}</Detail>
                <Detail label="Manager">{me.managerName ?? '—'}</Detail>
                <Detail label="Joined">{formatDate(me.joiningDate)}</Detail>
              </dl>
            )}
          </AsyncContent>
        </Card>
        <ChangePasswordCard />
      </div>
    </>
  )
}

function ChangePasswordCard() {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY_FORM)
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const set = (field) => (event) => setForm({ ...form, [field]: event.target.value })

  const submit = async (event) => {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    if (form.newPassword !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: 'Passwords do not match' })
      return
    }
    setSaving(true)
    try {
      await authApi.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
      toast.success('Password changed. Other devices have been signed out.')
      setForm(EMPTY_FORM)
    } catch (err) {
      setError(getErrorMessage(err))
      setFieldErrors(getFieldErrors(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card title="Change password">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorAlert message={error} />
        <TextInput id="currentPassword" label="Current password" type="password" autoComplete="current-password"
                   value={form.currentPassword} onChange={set('currentPassword')} error={fieldErrors.currentPassword} />
        <TextInput id="newPassword" label="New password" type="password" autoComplete="new-password"
                   value={form.newPassword} onChange={set('newPassword')} error={fieldErrors.newPassword}
                   hint="8–64 characters, with at least one letter and one number." />
        <TextInput id="confirmPassword" label="Confirm new password" type="password" autoComplete="new-password"
                   value={form.confirmPassword} onChange={set('confirmPassword')} error={fieldErrors.confirmPassword} />
        <Button type="submit" loading={saving}>Update password</Button>
      </form>
    </Card>
  )
}

function Detail({ label, children }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</dt>
      <dd className="mt-0.5 text-slate-700">{children}</dd>
    </div>
  )
}
