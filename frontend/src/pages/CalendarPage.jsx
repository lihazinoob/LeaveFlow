import { useMemo, useState } from 'react'
import { CalendarX2, ChevronLeft, ChevronRight } from 'lucide-react'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import { AsyncContent, EmptyState, PageHeader } from '../components/ui/Feedback'
import useAsync from '../hooks/useAsync'
import { dashboardApi } from '../api/endpoints'
import { formatDateRange, parseDate, pluralDays } from '../utils/format'

/** "YYYY-MM" for the month `offset` months away from the given one. */
function shiftMonth(month, offset) {
  const [year, m] = month.split('-').map(Number)
  const date = new Date(year, m - 1 + offset, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/**
 * Team leave calendar: one row per person who is away this month, one column per day.
 * Weekend and holiday columns are shaded; pending leave is striped so it stands out from approved leave.
 */
export default function CalendarPage() {
  const [month, setMonth] = useState(currentMonth)
  const calendar = useAsync(() => dashboardApi.calendar(month), [month])
  const data = calendar.data

  // Group absences by person: [{ employeeId, name, department, absences: [...] }]
  const rows = useMemo(() => {
    if (!data) return []
    const byEmployee = new Map()
    for (const absence of data.absences) {
      if (!byEmployee.has(absence.employeeId)) {
        byEmployee.set(absence.employeeId, {
          employeeId: absence.employeeId, name: absence.employeeName, department: absence.department, absences: [],
        })
      }
      byEmployee.get(absence.employeeId).absences.push(absence)
    }
    return [...byEmployee.values()].sort((a, b) => a.name.localeCompare(b.name))
  }, [data])

  const legend = useMemo(() => {
    const types = new Map()
    data?.absences.forEach((a) => types.set(a.leaveTypeName, a.color))
    return [...types.entries()]
  }, [data])

  const title = parseDate(`${month}-01`).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })
  const today = new Date().toDateString()

  return (
    <>
      <PageHeader
        title="Team calendar"
        description={data?.scope === 'ORGANISATION' ? 'Everyone in the company.' : 'People who report to you.'}
        actions={(
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" icon={ChevronLeft} aria-label="Previous month"
                    onClick={() => setMonth(shiftMonth(month, -1))} />
            <span className="w-36 text-center text-sm font-semibold text-slate-700">{title}</span>
            <Button variant="secondary" size="sm" icon={ChevronRight} aria-label="Next month"
                    onClick={() => setMonth(shiftMonth(month, 1))} />
            <Button variant="ghost" size="sm" onClick={() => setMonth(currentMonth())}>Today</Button>
          </div>
        )}
      />

      <Card bodyClassName="p-0">
        <AsyncContent {...calendar}>
          {data && rows.length === 0 && (
            <EmptyState icon={CalendarX2} title="Nobody is on leave this month" />
          )}
          {data && rows.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-xs">
                <thead>
                  <tr>
                    <th className="sticky left-0 z-10 min-w-36 bg-white px-3 py-2 text-left font-medium text-slate-500">Employee</th>
                    {data.days.map((day) => {
                      const date = parseDate(day.date)
                      const isToday = date.toDateString() === today
                      return (
                        <th key={day.date} title={day.holidayName ?? undefined}
                            className={`min-w-6 px-0 py-2 text-center font-medium
                              ${day.holidayName ? 'bg-rose-50 text-rose-600' : day.weekend ? 'bg-slate-100 text-slate-400' : 'text-slate-500'}`}>
                          <div className="text-[10px] uppercase">{date.toLocaleDateString('en-GB', { weekday: 'narrow' })}</div>
                          <div className={isToday ? 'mx-auto flex h-5 w-5 items-center justify-center rounded-full bg-brand-600 text-white' : ''}>
                            {date.getDate()}
                          </div>
                        </th>
                      )
                    })}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.employeeId} className="border-t border-slate-100">
                      <td className="sticky left-0 z-10 bg-white px-3 py-2">
                        <p className="font-medium text-slate-700">{row.name}</p>
                        <p className="text-slate-400">{row.department}</p>
                      </td>
                      {data.days.map((day) => {
                        const absence = row.absences.find((a) => a.startDate <= day.date && day.date <= a.endDate)
                        const offDay = day.weekend || day.holidayName
                        return (
                          <td key={day.date} className={`h-10 px-0 ${offDay ? 'bg-slate-50' : ''}`}>
                            {absence && (
                              <div
                                title={`${row.name}: ${absence.leaveTypeName} (${absence.status.toLowerCase()}), `
                                  + `${formatDateRange(absence.startDate, absence.endDate)}, ${pluralDays(absence.workingDays)}`}
                                className={`h-6 ${absence.status === 'PENDING' ? 'bar-pending opacity-70' : ''}
                                  ${day.date === absence.startDate ? 'ml-0.5 rounded-l-md' : ''}
                                  ${day.date === absence.endDate ? 'mr-0.5 rounded-r-md' : ''}`}
                                style={{ backgroundColor: absence.color }}
                              />
                            )}
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {data && (
            <div className="flex flex-wrap items-center gap-4 border-t border-slate-100 px-4 py-3 text-xs text-slate-500">
              {legend.map(([name, color]) => (
                <span key={name} className="flex items-center gap-1.5">
                  <span className="h-3 w-3 rounded-sm" style={{ backgroundColor: color }} />{name}
                </span>
              ))}
              <span className="flex items-center gap-1.5">
                <span className="bar-pending h-3 w-3 rounded-sm bg-slate-400 opacity-70" />Pending approval
              </span>
              <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm bg-slate-100" />Weekend</span>
              <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm bg-rose-50 ring-1 ring-rose-200" />Public holiday</span>
            </div>
          )}
        </AsyncContent>
      </Card>
    </>
  )
}
