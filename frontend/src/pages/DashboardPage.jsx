import { CalendarCheck2, ClipboardCheck, Plane, Plus, Users } from 'lucide-react'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import { LeaveTypeTag, StatusBadge } from '../components/ui/Badge'
import { AsyncContent, EmptyState, PageHeader } from '../components/ui/Feedback'
import BalanceCards from '../components/leave/BalanceCards'
import useLeaveDialogs from '../components/leave/useLeaveDialogs'
import StatTile from '../components/dashboard/StatTile'
import { LeaveTypePie, TotalsBarChart } from '../components/dashboard/Charts'
import useAsync from '../hooks/useAsync'
import { useAuth } from '../auth/AuthContext'
import { dashboardApi } from '../api/endpoints'
import { formatDate, formatDateRange, pluralDays, titleCase } from '../utils/format'

/**
 * One page for every role, built from sections:
 *  - everyone:       own balances, upcoming leave, next holidays
 *  - manager/admin:  who is away today / soon, pending approvals
 *  - admin:          company KPIs and charts
 */
export default function DashboardPage() {
  const { user, hasRole } = useAuth()
  const me = useAsync(() => dashboardApi.me(), [])
  const { openDetail, openApply, dialogs } = useLeaveDialogs(me.reload)

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${user.fullName.split(' ')[0]}`}
        description={new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
        actions={<Button icon={Plus} onClick={openApply}>Apply for leave</Button>}
      />

      <AsyncContent {...me}>
        {me.data && (
          <div className="space-y-6">
            <BalanceCards balances={me.data.balances} />
            <div className="grid gap-6 lg:grid-cols-3">
              <Card title="My upcoming leave" className="lg:col-span-2" bodyClassName="p-0">
                {me.data.upcomingLeaves.length === 0 ? (
                  <EmptyState icon={Plane} title="No upcoming leave" description="Time for a break? Apply above." />
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {me.data.upcomingLeaves.map((leave) => (
                      <li key={leave.id}>
                        <button type="button" onClick={() => openDetail(leave)}
                                className="flex w-full items-center justify-between gap-3 px-5 py-3 text-left hover:bg-slate-50">
                          <div>
                            <LeaveTypeTag name={leave.leaveTypeName} color={leave.leaveTypeColor} />
                            <p className="mt-0.5 text-sm text-slate-500">
                              {formatDateRange(leave.startDate, leave.endDate)} · {pluralDays(leave.workingDays)}
                            </p>
                          </div>
                          <StatusBadge status={leave.status} />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
              <Card title="Upcoming holidays" bodyClassName="p-0">
                {me.data.upcomingHolidays.length === 0 ? <EmptyState title="No holidays coming up" /> : (
                  <ul className="divide-y divide-slate-100">
                    {me.data.upcomingHolidays.map((holiday) => (
                      <li key={holiday.id} className="px-5 py-3">
                        <p className="text-sm font-medium text-slate-700">{holiday.name}</p>
                        <p className="text-xs text-slate-400">{formatDate(holiday.date)} · {titleCase(holiday.dayOfWeek)}</p>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </div>
          </div>
        )}
      </AsyncContent>

      {hasRole('MANAGER', 'ADMIN') && <TeamSection />}
      {hasRole('ADMIN') && <OrganisationSection />}
      {dialogs}
    </>
  )
}

function TeamSection() {
  const team = useAsync(() => dashboardApi.team(), [])
  const data = team.data
  const scopeLabel = data?.scope === 'ORGANISATION' ? 'Company' : 'My team'

  return (
    <section className="mt-8">
      <h2 className="mb-3 text-base font-semibold text-slate-800">{scopeLabel}</h2>
      <AsyncContent {...team}>
        {data && (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-3">
              <StatTile label={data.scope === 'ORGANISATION' ? 'Active employees' : 'People in my team'}
                        value={data.teamSize} icon={Users} tone="sky" />
              <StatTile label="On leave today" value={data.onLeaveToday.length} icon={Plane} tone="emerald"
                        to="/calendar" />
              <StatTile label="Waiting for my approval" value={data.pendingApprovals} icon={ClipboardCheck}
                        tone="amber" to="/approvals" />
            </div>
            <div className="grid gap-6 lg:grid-cols-2">
              <AbsenceList title="Away today" items={data.onLeaveToday} empty="Everyone is in today" />
              <AbsenceList title="Starting in the next 14 days" items={data.upcomingAbsences}
                           empty="No leave planned in the next two weeks" />
            </div>
          </div>
        )}
      </AsyncContent>
    </section>
  )
}

function AbsenceList({ title, items, empty }) {
  return (
    <Card title={title} bodyClassName="p-0">
      {items.length === 0 ? <EmptyState icon={CalendarCheck2} title={empty} /> : (
        <ul className="divide-y divide-slate-100">
          {items.map((item) => (
            <li key={item.requestId} className="flex items-center justify-between gap-3 px-5 py-3">
              <div>
                <p className="text-sm font-medium text-slate-700">{item.employeeName}</p>
                <p className="text-xs text-slate-400">
                  {formatDateRange(item.startDate, item.endDate)} · {item.leaveTypeName}
                </p>
              </div>
              {item.status === 'PENDING'
                ? <StatusBadge status="PENDING" />
                : <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: item.color }} />}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function OrganisationSection() {
  const org = useAsync(() => dashboardApi.organisation(), [])
  const data = org.data
  return (
    <section className="mt-8">
      <h2 className="mb-3 text-base font-semibold text-slate-800">Leave analytics {data && `(${data.year})`}</h2>
      <AsyncContent {...org}>
        {data && (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatTile label="Active employees" value={data.activeEmployees} icon={Users} tone="sky" to="/employees" />
              <StatTile label="On leave today" value={data.onLeaveToday} icon={Plane} tone="emerald" />
              <StatTile label="Pending requests" value={data.pendingRequests} icon={ClipboardCheck} tone="amber"
                        to="/approvals" />
              <StatTile label="Approved leave days this year" value={data.approvedDaysThisYear} icon={CalendarCheck2} />
            </div>
            <div className="grid gap-6 lg:grid-cols-3">
              <Card title="Approved days by leave type">
                <LeaveTypePie data={data.approvedDaysByType} />
              </Card>
              <Card title="Approved days per month" className="lg:col-span-2">
                <TotalsBarChart data={data.approvedDaysByMonth} />
              </Card>
            </div>
            <div className="grid gap-6 lg:grid-cols-2">
              <Card title="Approved days by department">
                <TotalsBarChart data={data.approvedDaysByDepartment} color="#0ea5e9" height={220} />
              </Card>
              <Card title="Headcount by department">
                <TotalsBarChart data={data.headcountByDepartment} color="#10b981" height={220} />
              </Card>
            </div>
          </div>
        )}
      </AsyncContent>
    </section>
  )
}

function greeting() {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}
