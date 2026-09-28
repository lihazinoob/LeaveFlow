import { LeaveTypeTag, StatusBadge } from '../ui/Badge'
import { EmptyState } from '../ui/Feedback'
import { formatDateRange, pluralDays } from '../../utils/format'

/** A list of leave requests. Clicking a row opens its details. */
export default function LeaveTable({ leaves, onSelect, showEmployee = false, emptyTitle = 'No leave requests' }) {
  if (!leaves?.length) {
    return <EmptyState title={emptyTitle} />
  }
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-100 text-sm">
        <thead>
          <tr className="text-left text-xs font-medium uppercase tracking-wide text-slate-400">
            {showEmployee && <th className="px-4 py-2">Employee</th>}
            <th className="px-4 py-2">Type</th>
            <th className="px-4 py-2">Dates</th>
            <th className="px-4 py-2">Days</th>
            <th className="px-4 py-2">Status</th>
            <th className="hidden px-4 py-2 md:table-cell">Reason</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {leaves.map((leave) => (
            <tr key={leave.id} onClick={() => onSelect(leave)}
                className="cursor-pointer hover:bg-slate-50">
              {showEmployee && (
                <td className="px-4 py-3">
                  <p className="font-medium text-slate-700">{leave.employeeName}</p>
                  <p className="text-xs text-slate-400">{leave.department}</p>
                </td>
              )}
              <td className="px-4 py-3"><LeaveTypeTag name={leave.leaveTypeName} color={leave.leaveTypeColor} /></td>
              <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateRange(leave.startDate, leave.endDate)}</td>
              <td className="whitespace-nowrap px-4 py-3 text-slate-600">{pluralDays(leave.workingDays)}</td>
              <td className="px-4 py-3"><StatusBadge status={leave.status} /></td>
              <td className="hidden max-w-xs truncate px-4 py-3 text-slate-500 md:table-cell">{leave.reason}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
