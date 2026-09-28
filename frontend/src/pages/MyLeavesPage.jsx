import { useState } from 'react'
import { Plus } from 'lucide-react'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import { SelectInput } from '../components/ui/Field'
import { AsyncContent, PageHeader } from '../components/ui/Feedback'
import LeaveTable from '../components/leave/LeaveTable'
import useLeaveDialogs from '../components/leave/useLeaveDialogs'
import BalanceCards from '../components/leave/BalanceCards'
import useAsync from '../hooks/useAsync'
import { leaveApi } from '../api/endpoints'

const STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']

export default function MyLeavesPage() {
  const currentYear = new Date().getFullYear()
  const [status, setStatus] = useState('')
  const [year, setYear] = useState(currentYear)

  const leaves = useAsync(() => leaveApi.mine({ status: status || undefined, year }), [status, year])
  const balances = useAsync(() => leaveApi.balance({ year }), [year])

  const reloadAll = () => {
    leaves.reload()
    balances.reload()
  }
  const { openDetail, openApply, dialogs } = useLeaveDialogs(reloadAll)

  return (
    <>
      <PageHeader
        title="My leaves"
        description="Your leave requests and remaining balance."
        actions={<Button icon={Plus} onClick={openApply}>Apply for leave</Button>}
      />

      <AsyncContent {...balances}>
        <BalanceCards balances={balances.data} />
      </AsyncContent>

      <Card
        className="mt-6"
        bodyClassName="p-0"
        title="Requests"
        action={(
          <div className="flex gap-2">
            <SelectInput id="status" aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {STATUSES.map((s) => <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
            </SelectInput>
            <SelectInput id="year" aria-label="Year" value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {[currentYear + 1, currentYear, currentYear - 1].map((y) => <option key={y} value={y}>{y}</option>)}
            </SelectInput>
          </div>
        )}
      >
        <AsyncContent {...leaves}>
          <LeaveTable leaves={leaves.data} onSelect={openDetail} emptyTitle="No leave requests for this filter" />
        </AsyncContent>
      </Card>

      {dialogs}
    </>
  )
}
