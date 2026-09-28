import { useEffect, useMemo, useState } from 'react'
import { CheckCircle2, Info } from 'lucide-react'
import Modal from '../ui/Modal'
import Button from '../ui/Button'
import { SelectInput, TextArea, TextInput } from '../ui/Field'
import { ErrorAlert } from '../ui/Feedback'
import useAsync from '../../hooks/useAsync'
import { leaveApi } from '../../api/endpoints'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { pluralDays, todayIso } from '../../utils/format'
import { useToast } from '../ui/Toast'

/**
 * Apply for leave, or edit a pending request (when `leave` is given).
 *
 * While the user picks dates, the form asks the backend for a preview (debounced by 350 ms).
 * The preview shows the working days and the remaining balance, and lists every rule the
 * request would break, so the user knows before submitting. The same rules run again on submit.
 *
 * Render it only while open ({open && <LeaveFormModal/>}), so the form starts fresh every time.
 */
export default function LeaveFormModal({ leave, onClose, onSaved }) {
  const toast = useToast()
  const editing = Boolean(leave)
  const [form, setForm] = useState({
    leaveTypeId: leave?.leaveTypeId ?? '',
    startDate: leave?.startDate ?? '',
    endDate: leave?.endDate ?? '',
    reason: leave?.reason ?? '',
  })
  const [preview, setPreview] = useState(null)
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const { data: types } = useAsync(() => leaveApi.types(), [])

  // Identifies the inputs a preview belongs to, so an outdated preview is never shown.
  const previewKey = form.leaveTypeId && form.startDate && form.endDate
    ? `${form.leaveTypeId}|${form.startDate}|${form.endDate}` : null

  useEffect(() => {
    if (!previewKey) return undefined
    const timer = setTimeout(() => {
      leaveApi.preview({
        leaveTypeId: form.leaveTypeId,
        startDate: form.startDate,
        endDate: form.endDate,
        excludeRequestId: leave?.id,
      })
        .then((data) => setPreview({ key: previewKey, ...data }))
        .catch((err) => setPreview({ key: previewKey, problems: [getErrorMessage(err)], valid: false }))
    }, 350)
    return () => clearTimeout(timer)
  }, [previewKey, form.leaveTypeId, form.startDate, form.endDate, leave?.id])

  const currentPreview = preview?.key === previewKey ? preview : null
  const selectedType = useMemo(
    () => types?.find((type) => String(type.id) === String(form.leaveTypeId)),
    [types, form.leaveTypeId],
  )

  const update = (field) => (event) => {
    const value = event.target.value
    setForm((current) => {
      const next = { ...current, [field]: value }
      // Picking a start date after the end date moves the end date along with it.
      if (field === 'startDate' && (!current.endDate || current.endDate < value)) next.endDate = value
      return next
    })
  }

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setFieldErrors({})
    const body = { ...form, leaveTypeId: Number(form.leaveTypeId), reason: form.reason.trim() }
    try {
      const saved = editing ? await leaveApi.update(leave.id, body) : await leaveApi.apply(body)
      toast.success(editing ? 'Leave request updated' : 'Leave request submitted for approval')
      onSaved(saved)
    } catch (err) {
      setError(getErrorMessage(err))
      setFieldErrors(getFieldErrors(err))
    } finally {
      setSaving(false)
    }
  }

  const blocked = currentPreview && !currentPreview.valid

  return (
    <Modal
      open
      title={editing ? 'Edit leave request' : 'Apply for leave'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="leave-form" loading={saving} disabled={blocked || !previewKey}>
            {editing ? 'Save changes' : 'Submit request'}
          </Button>
        </>
      )}
    >
      <form id="leave-form" onSubmit={submit} className="space-y-4" noValidate>
        <ErrorAlert message={error} />

        <SelectInput id="leaveTypeId" label="Leave type" value={form.leaveTypeId} onChange={update('leaveTypeId')}
                     error={fieldErrors.leaveTypeId}
                     hint={selectedType && (selectedType.annualQuota == null
                       ? 'No yearly limit'
                       : `${selectedType.annualQuota} working days per year`)}>
          <option value="">Select a leave type…</option>
          {types?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
        </SelectInput>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextInput id="startDate" label="From" type="date" min={todayIso()} value={form.startDate}
                     onChange={update('startDate')} error={fieldErrors.startDate} />
          <TextInput id="endDate" label="To" type="date" min={form.startDate || todayIso()} value={form.endDate}
                     onChange={update('endDate')} error={fieldErrors.endDate} />
        </div>

        {previewKey && <PreviewBox preview={currentPreview} />}

        <TextArea id="reason" label="Reason" maxLength={500} value={form.reason} onChange={update('reason')}
                  error={fieldErrors.reason} placeholder="e.g. Family event" />
      </form>
    </Modal>
  )
}

function PreviewBox({ preview }) {
  if (!preview) {
    return <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">Calculating working days…</p>
  }
  return (
    <div className={`rounded-lg px-3 py-2 text-sm ${preview.valid ? 'bg-emerald-50 text-emerald-800' : 'bg-amber-50 text-amber-800'}`}>
      <p className="flex items-center gap-1.5 font-medium">
        {preview.valid ? <CheckCircle2 className="h-4 w-4" /> : <Info className="h-4 w-4" />}
        {pluralDays(preview.workingDays ?? 0)} of leave
        {preview.availableDays != null && ` · ${pluralDays(preview.availableDays)} available`}
      </p>
      <p className="mt-0.5 text-xs opacity-80">Weekends (Fri–Sat) and public holidays are not counted.</p>
      {preview.problems?.length > 0 && (
        <ul className="mt-2 list-disc space-y-0.5 pl-5">
          {preview.problems.map((problem) => <li key={problem}>{problem}</li>)}
        </ul>
      )}
    </div>
  )
}
