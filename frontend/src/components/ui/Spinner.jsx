export default function Spinner({ label, className = '' }) {
  return (
    <div className={`flex items-center gap-3 text-sm text-slate-500 ${className}`} role="status">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-brand-600" />
      {label && <span>{label}</span>}
    </div>
  )
}
