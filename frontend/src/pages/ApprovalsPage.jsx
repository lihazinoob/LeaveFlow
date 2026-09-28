import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import { SelectInput, TextInput } from '../components/ui/Field'
import { AsyncContent, PageHeader } from '../components/ui/Feedback'
import LeaveTable from '../components/leave/LeaveTable'
import useLeaveDialogs from '../components/leave/useLeaveDialogs'
import useAsync from '../hooks/useAsync'
import useDebouncedValue from '../hooks/useDebouncedValue'
import { useAuth } from '../auth/AuthContext'
import { leaveApi } from '../api/endpoints'

const STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']

/**
 * Tabs:
 *  - Waiting for me: pending requests this user can approve or reject.
 *  - My team (manager): every request of the direct reports.
 *  - All requests (admin): the whole organisation, filtered and paged.
 */
export default function ApprovalsPage() {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const [tab, setTab] = useState('pending')
  const [refreshKey, setRefreshKey] = useState(0)
  const { openDetail, dialogs } = useLeaveDialogs(() => setRefreshKey((k) => k + 1))

  const tabs = [
    { id: 'pending', label: 'Waiting for me' },
    isAdmin ? { id: 'all', label: 'All requests' } : { id: 'team', label: 'My team' },
  ]

  return (
    <>
      <PageHeader title="Approvals" description="Review leave requests and follow your team's leave." />

      <div className="mb-4 flex gap-1 rounded-lg bg-slate-100 p-1 sm:w-fit">
        {tabs.map((t) => (
          <button key={t.id} type="button" onClick={() => setTab(t.id)}
                  className={`flex-1 rounded-md px-4 py-1.5 text-sm font-medium sm:flex-none
                    ${tab === t.id ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}>
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'pending' && <PendingTab refreshKey={refreshKey} onSelect={openDetail} />}
      {tab === 'team' && <TeamTab refreshKey={refreshKey} onSelect={openDetail} />}
      {tab === 'all' && <AllRequestsTab refreshKey={refreshKey} onSelect={openDetail} />}

      {dialogs}
    </>
  )
}

function PendingTab({ refreshKey, onSelect }) {
  const pending = useAsync(() => leaveApi.approvals(), [refreshKey])
  return (
    <Card bodyClassName="p-0" title={`Pending requests${pending.data ? ` (${pending.data.length})` : ''}`}>
      <AsyncContent {...pending}>
        <LeaveTable leaves={pending.data} onSelect={onSelect} showEmployee
                    emptyTitle="Nothing to review. You're all caught up!" />
      </AsyncContent>
    </Card>
  )
}

function TeamTab({ refreshKey, onSelect }) {
  const [status, setStatus] = useState('')
  const team = useAsync(() => leaveApi.team({ status: status || undefined }), [status, refreshKey])
  return (
    <Card bodyClassName="p-0" title="Team requests" action={<StatusFilter value={status} onChange={setStatus} />}>
      <AsyncContent {...team}>
        <LeaveTable leaves={team.data} onSelect={onSelect} showEmployee />
      </AsyncContent>
    </Card>
  )
}

function AllRequestsTab({ refreshKey, onSelect }) {
  const [filters, setFilters] = useState({ status: '', employee: '' })
  const [page, setPage] = useState(0)
  const employeeSearch = useDebouncedValue(filters.employee.trim())
  const all = useAsync(
    () => leaveApi.search({
      status: filters.status || undefined,
      employee: employeeSearch || undefined,
      page,
      size: 10,
    }),
    [filters.status, employeeSearch, page, refreshKey],
  )
  const changeFilter = (field, value) => {
    setFilters((current) => ({ ...current, [field]: value }))
    setPage(0)
  }

  return (
    <Card
      bodyClassName="p-0"
      title="All requests"
      action={(
        <div className="flex gap-2">
          <TextInput id="employee-search" aria-label="Search employee" placeholder="Search employee…"
                     value={filters.employee} onChange={(e) => changeFilter('employee', e.target.value)} />
          <StatusFilter value={filters.status} onChange={(value) => changeFilter('status', value)} />
        </div>
      )}
    >
      <AsyncContent {...all}>
        <LeaveTable leaves={all.data?.content} onSelect={onSelect} showEmployee />
        {all.data && all.data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3 text-sm text-slate-500">
            <span>Page {all.data.page + 1} of {all.data.totalPages} · {all.data.totalElements} requests</span>
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" icon={ChevronLeft} disabled={page === 0}
                      onClick={() => setPage(page - 1)}>Previous</Button>
              <Button variant="secondary" size="sm" disabled={page + 1 >= all.data.totalPages}
                      onClick={() => setPage(page + 1)}>Next <ChevronRight className="h-4 w-4" /></Button>
            </div>
          </div>
        )}
      </AsyncContent>
    </Card>
  )
}

function StatusFilter({ value, onChange }) {
  return (
    <SelectInput id="status-filter" aria-label="Status" value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">All statuses</option>
      {STATUSES.map((s) => <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
    </SelectInput>
  )
}
