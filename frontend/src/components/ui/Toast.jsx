import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'

const ToastContext = createContext(null)
let nextToastId = 1

/** Small notifications in the corner, e.g. "Leave request submitted". They disappear after 4 seconds. */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const show = useCallback((message, type) => {
    const id = nextToastId++
    setToasts((current) => [...current, { id, message, type }])
    setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 4000)
  }, [])

  const api = useMemo(() => ({
    success: (message) => show(message, 'success'),
    error: (message) => show(message, 'error'),
  }), [show])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 flex-col gap-2" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id}
               className="pointer-events-auto flex items-start gap-2 rounded-lg bg-white px-4 py-3 text-sm shadow-lg ring-1 ring-slate-200">
            {toast.type === 'success'
              ? <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-500" />
              : <XCircle className="h-5 w-5 shrink-0 text-red-500" />}
            <span className="text-slate-700">{toast.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  return useContext(ToastContext)
}
