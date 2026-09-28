import { Link } from 'react-router'

/** A headline number (KPI). When `to` is given, the tile links to the related page. */
export default function StatTile({ label, value, icon: Icon, to, tone = 'brand' }) {
  const tones = {
    brand: 'bg-brand-50 text-brand-600',
    amber: 'bg-amber-50 text-amber-600',
    emerald: 'bg-emerald-50 text-emerald-600',
    sky: 'bg-sky-50 text-sky-600',
  }
  const content = (
    <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-colors hover:border-slate-300">
      {Icon && (
        <span className={`flex h-10 w-10 items-center justify-center rounded-lg ${tones[tone]}`}>
          <Icon className="h-5 w-5" />
        </span>
      )}
      <div>
        <p className="text-2xl font-semibold text-slate-900">{value}</p>
        <p className="text-sm text-slate-500">{label}</p>
      </div>
    </div>
  )
  return to ? <Link to={to}>{content}</Link> : content
}
