/** Form controls with a label and an optional error message underneath. */

const CONTROL = `block w-full rounded-lg border-0 px-3 py-2 text-sm text-slate-800 ring-1 ring-inset
  placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-brand-500 disabled:bg-slate-50`

function controlClass(error) {
  return `${CONTROL} ${error ? 'ring-red-400' : 'ring-slate-300'}`
}

export function Field({ label, htmlFor, error, hint, children, className = '' }) {
  return (
    <div className={className}>
      {label && <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium text-slate-700">{label}</label>}
      {children}
      {error ? <p className="mt-1 text-xs text-red-600">{error}</p>
        : hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

export function TextInput({ label, id, error, hint, className, ...props }) {
  return (
    <Field label={label} htmlFor={id} error={error} hint={hint} className={className}>
      <input id={id} className={controlClass(error)} {...props} />
    </Field>
  )
}

export function SelectInput({ label, id, error, hint, className, children, ...props }) {
  return (
    <Field label={label} htmlFor={id} error={error} hint={hint} className={className}>
      <select id={id} className={controlClass(error)} {...props}>{children}</select>
    </Field>
  )
}

export function TextArea({ label, id, error, hint, className, ...props }) {
  return (
    <Field label={label} htmlFor={id} error={error} hint={hint} className={className}>
      <textarea id={id} rows={3} className={controlClass(error)} {...props} />
    </Field>
  )
}
