import { pluralDays } from '../../utils/format'

/** One card per leave type with a bar: used (solid) + pending (light) out of the yearly quota. */
export default function BalanceCards({ balances }) {
  if (!balances) return null
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {balances.map((balance) => {
        const unlimited = balance.annualQuota == null
        const usedPct = unlimited ? 0 : (balance.used / balance.annualQuota) * 100
        const pendingPct = unlimited ? 0 : (balance.pending / balance.annualQuota) * 100
        return (
          <div key={balance.leaveTypeId} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: balance.color }} />
              <p className="text-sm font-medium text-slate-600">{balance.leaveTypeName}</p>
            </div>
            <p className="mt-3 text-2xl font-semibold text-slate-900">
              {unlimited ? '∞' : balance.available}
              <span className="ml-1 text-sm font-normal text-slate-400">
                {unlimited ? 'no limit' : `of ${balance.annualQuota} left`}
              </span>
            </p>
            {!unlimited && (
              <div className="mt-3 flex h-2 overflow-hidden rounded-full bg-slate-100">
                <div style={{ width: `${usedPct}%`, backgroundColor: balance.color }} />
                <div style={{ width: `${pendingPct}%`, backgroundColor: balance.color, opacity: 0.35 }} />
              </div>
            )}
            <p className="mt-2 text-xs text-slate-500">
              {pluralDays(balance.used)} used · {pluralDays(balance.pending)} pending
            </p>
          </div>
        )
      })}
    </div>
  )
}
