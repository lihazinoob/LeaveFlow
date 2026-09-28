import { useState } from 'react'
import LeaveDetailModal from './LeaveDetailModal'
import LeaveFormModal from './LeaveFormModal'

/**
 * Shared wiring for the leave dialogs used on several pages:
 * the details dialog (with approve/reject/cancel) and the apply/edit form.
 *
 * const { openDetail, openApply, dialogs } = useLeaveDialogs(reload)
 * ...then render {dialogs} somewhere in the page.
 */
export default function useLeaveDialogs(onChanged) {
  const [detailId, setDetailId] = useState(null)
  const [form, setForm] = useState(null)

  const dialogs = (
    <>
      {detailId && (
        <LeaveDetailModal
          leaveId={detailId}
          onClose={() => setDetailId(null)}
          onChanged={onChanged}
          onEdit={(leave) => {
            setDetailId(null)
            setForm({ leave })
          }}
        />
      )}
      {form && (
        <LeaveFormModal
          leave={form.leave}
          onClose={() => setForm(null)}
          onSaved={() => {
            setForm(null)
            onChanged()
          }}
        />
      )}
    </>
  )

  return {
    openDetail: (leave) => setDetailId(leave.id),
    openApply: () => setForm({}),
    dialogs,
  }
}
