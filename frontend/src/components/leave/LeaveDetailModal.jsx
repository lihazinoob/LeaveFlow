import { useState } from 'react'
import { Check, Pencil, Undo2, X } from 'lucide-react'
import Modal from '../ui/Modal'
import Button from '../ui/Button'
import { LeaveTypeTag, StatusBadge } from '../ui/Badge'
import { AsyncContent, ErrorAlert } from '../ui/Feedback'
import { TextArea } from '../ui/Field'
import { useToast } from '../ui/Toast'
import useAsync from '../../hooks/useAsync'
import { leaveApi } from '../../api/endpoints'
import { getErrorMessage } from '../../utils/errors'
import { formatDateRange, formatDateTime, pluralDays, titleCase } from '../../utils/format'

/**
 * One leave request with its audit trail and the actions the current user may take.
 * The buttons follow the canEdit / canCancel / canReview flags computed by the backend.
 */
export default function LeaveDetailModal({ leaveId, onClose, onChanged, onEdit }) {
  const toast = useToast()
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)

  const leaveState = useAsync(() => leaveApi.get(leaveId), [leaveId])
  const historyState = useAsync(() => leaveApi.history(leaveId), [leaveId])
  const leave = leaveState.data

  const act = async (action, apiCall, successMessage) => {
    setBusy(action)
    setError(null)
    try {
      await apiCall(leaveId, comment.trim() || null)
      toast.success(successMessage)
      onChanged()
      onClose()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy(null)
    }
  }

  const anyAction = leave && (leave.canReview || leave.canCancel)

  return (
    <Modal open title="Leave request" onClose={onClose} size="lg"
           footer={leave && (
             <>
               {leave.canEdit && (
                 <Button variant="secondary" icon={Pencil} onClick={() => onEdit(leave)}>Edit</Button>
               )}
               {leave.canCancel && (
                 <Button variant="secondary" icon={Undo2} loading={busy === 'cancel'}
                         onClick={() => act('cancel', leaveApi.cancel, 'Leave request cancelled')}>
                   Cancel request
                 </Button>
               )}
               {leave.canReview && (
                 <>
                   <Button variant="danger" icon={X} loading={busy === 'reject'}
                           onClick={() => act('reject', leaveApi.reject, 'Leave request rejected')}>
                     Reject
                   </Button>
                   <Button variant="success" icon={Check} loading={busy === 'approve'}
                           onClick={() => act('approve', leaveApi.approve, 'Leave request approved')}>
                     Approve
                   </Button>
                 </>
               )}
             </>
           )}>
      <AsyncContent {...leaveState}>
        {leave && (
          <div className="space-y-5">
            <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
              <Item label="Employee">{leave.employeeName} <span className="text-slate-400">({leave.employeeCode})</span></Item>
              <Item label="Status"><StatusBadge status={leave.status} /></Item>
              <Item label="Leave type"><LeaveTypeTag name={leave.leaveTypeName} color={leave.leaveTypeColor} /></Item>
              <Item label="Dates">{formatDateRange(leave.startDate, leave.endDate)} · {pluralDays(leave.workingDays)}</Item>
              <Item label="Reason" wide>{leave.reason}</Item>
              {leave.reviewedByName && (
                <Item label="Reviewed by" wide>
                  {leave.reviewedByName} on {formatDateTime(leave.reviewedAt)}
                  {leave.reviewComment && <span className="block text-slate-500">“{leave.reviewComment}”</span>}
                </Item>
              )}
            </dl>

            {anyAction && (
              <TextArea id="comment" label="Comment" maxLength={500} value={comment}
                        onChange={(e) => setComment(e.target.value)}
                        hint={leave.canReview ? 'Required when rejecting; optional otherwise.' : 'Optional.'} />
            )}
            <ErrorAlert message={error} />

            <div>
              <h3 className="mb-3 text-sm font-semibold text-slate-700">History</h3>
              <AsyncContent {...historyState}>
                <ol className="relative space-y-4 border-l border-slate-200 pl-5">
                  {historyState.data?.map((entry) => (
                    <li key={entry.id} className="relative">
                      <span className="absolute -left-[25px] top-1 h-2.5 w-2.5 rounded-full bg-brand-500 ring-4 ring-white" />
                      <p className="text-sm text-slate-700">
                        {historyLabel(entry)}
                        <span className="text-slate-400"> by {entry.changedByName}</span>
                      </p>
                      <p className="text-xs text-slate-400">{formatDateTime(entry.changedAt)}</p>
                      {entry.comment && <p className="mt-0.5 text-sm text-slate-500">“{entry.comment}”</p>}
                    </li>
                  ))}
                </ol>
              </AsyncContent>
            </div>
            <p className="text-xs text-slate-400">Requested on {formatDateTime(leave.createdAt)}</p>
          </div>
        )}
      </AsyncContent>
    </Modal>
  )
}

/** "Submitted", "Edited" (status unchanged) or a transition such as "Pending → Approved". */
function historyLabel(entry) {
  if (entry.fromStatus == null) return 'Submitted'
  if (entry.fromStatus === entry.toStatus) return 'Edited'
  return `${titleCase(entry.fromStatus)} → ${titleCase(entry.toStatus)}`
}

function Item({ label, wide, children }) {
  return (
    <div className={wide ? 'sm:col-span-2' : ''}>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</dt>
      <dd className="mt-0.5 text-slate-700">{children}</dd>
    </div>
  )
}
