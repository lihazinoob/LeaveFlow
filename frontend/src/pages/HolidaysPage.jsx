import { useState } from 'react'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import Modal from '../components/ui/Modal'
import { SelectInput, TextInput } from '../components/ui/Field'
import { AsyncContent, EmptyState, ErrorAlert, PageHeader } from '../components/ui/Feedback'
import { useToast } from '../components/ui/Toast'
import useAsync from '../hooks/useAsync'
import { useAuth } from '../auth/AuthContext'
import { holidayApi } from '../api/endpoints'
import { getErrorMessage, getFieldErrors } from '../utils/errors'
import { formatDate, titleCase } from '../utils/format'

/** Public holidays: everyone can read them; admins can add, edit and delete. */
export default function HolidaysPage() {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const toast = useToast()
  const currentYear = new Date().getFullYear()
  const [year, setYear] = useState(currentYear)
  const [editing, setEditing] = useState(null)
  const holidays = useAsync(() => holidayApi.list(year), [year])

  const remove = async (holiday) => {
    if (!window.confirm(`Delete "${holiday.name}" on ${formatDate(holiday.date)}?`)) return
    try {
      await holidayApi.remove(holiday.id)
      toast.success('Holiday deleted')
      holidays.reload()
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Public holidays"
        description="These days (and Fridays/Saturdays) are not counted when you take leave."
        actions={(
          <>
            <SelectInput id="year" aria-label="Year" value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {[currentYear - 1, currentYear, currentYear + 1].map((y) => <option key={y} value={y}>{y}</option>)}
            </SelectInput>
            {isAdmin && <Button icon={Plus} onClick={() => setEditing({})}>Add holiday</Button>}
          </>
        )}
      />

      <Card bodyClassName="p-0">
        <AsyncContent {...holidays}>
          {holidays.data?.length === 0 ? (
            <EmptyState title={`No holidays recorded for ${year}`} />
          ) : (
            <ul className="divide-y divide-slate-100">
              {holidays.data?.map((holiday) => (
                <li key={holiday.id} className="flex items-center gap-4 px-5 py-3">
                  <div className="w-28 shrink-0">
                    <p className="text-sm font-medium text-slate-700">{formatDate(holiday.date)}</p>
                    <p className="text-xs text-slate-400">{titleCase(holiday.dayOfWeek)}</p>
                  </div>
                  <p className="flex-1 text-sm text-slate-700">{holiday.name}</p>
                  {isAdmin && (
                    <div className="flex gap-1">
                      <Button variant="ghost" size="sm" icon={Pencil} aria-label="Edit"
                              onClick={() => setEditing(holiday)} />
                      <Button variant="ghost" size="sm" icon={Trash2} aria-label="Delete"
                              onClick={() => remove(holiday)} />
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </AsyncContent>
      </Card>

      {editing && (
        <HolidayFormModal holiday={editing.id ? editing : null} onClose={() => setEditing(null)}
                          onSaved={() => {
                            setEditing(null)
                            holidays.reload()
                          }} />
      )}
    </>
  )
}

function HolidayFormModal({ holiday, onClose, onSaved }) {
  const toast = useToast()
  const [form, setForm] = useState({ date: holiday?.date ?? '', name: holiday?.name ?? '' })
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setFieldErrors({})
    try {
      if (holiday) await holidayApi.update(holiday.id, form)
      else await holidayApi.create(form)
      toast.success(holiday ? 'Holiday updated' : 'Holiday added')
      onSaved()
    } catch (err) {
      setError(getErrorMessage(err))
      setFieldErrors(getFieldErrors(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open title={holiday ? 'Edit holiday' : 'Add holiday'} onClose={onClose}
           footer={(
             <>
               <Button variant="secondary" onClick={onClose}>Cancel</Button>
               <Button type="submit" form="holiday-form" loading={saving}>Save</Button>
             </>
           )}>
      <form id="holiday-form" onSubmit={submit} className="space-y-4" noValidate>
        <ErrorAlert message={error} />
        <TextInput id="holiday-date" label="Date" type="date" value={form.date} error={fieldErrors.date}
                   onChange={(e) => setForm({ ...form, date: e.target.value })} />
        <TextInput id="holiday-name" label="Name" maxLength={100} value={form.name} error={fieldErrors.name}
                   onChange={(e) => setForm({ ...form, name: e.target.value })} />
      </form>
    </Modal>
  )
}
