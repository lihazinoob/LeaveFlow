import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { EmptyState } from '../ui/Feedback'

/** Chart wrappers for the admin dashboard. Each takes a list of { label, color, total } from the API. */

const BRAND = '#6366f1'
const tooltipFormatter = (value) => [`${value} day${value === 1 ? '' : 's'}`, 'Approved leave']

export function LeaveTypePie({ data }) {
  const items = data.filter((item) => item.total > 0)
  if (items.length === 0) return <EmptyState title="No approved leave yet this year" />
  return (
    <ResponsiveContainer width="100%" height={260}>
      <PieChart>
        <Pie data={items} dataKey="total" nameKey="label" innerRadius={55} outerRadius={90} paddingAngle={2}>
          {items.map((item) => <Cell key={item.label} fill={item.color ?? BRAND} />)}
        </Pie>
        <Tooltip formatter={tooltipFormatter} />
        <Legend iconType="circle" wrapperStyle={{ fontSize: 12 }} />
      </PieChart>
    </ResponsiveContainer>
  )
}

export function TotalsBarChart({ data, color = BRAND, height = 260 }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
        <XAxis dataKey="label" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
        <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
        <Tooltip formatter={tooltipFormatter} cursor={{ fill: '#f1f5f9' }} />
        <Bar dataKey="total" fill={color} radius={[4, 4, 0, 0]} maxBarSize={40} />
      </BarChart>
    </ResponsiveContainer>
  )
}
