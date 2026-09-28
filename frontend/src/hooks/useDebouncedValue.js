import { useEffect, useState } from 'react'

/** Returns `value` only after it stopped changing for `delay` ms (e.g. to search after typing pauses). */
export default function useDebouncedValue(value, delay = 300) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}
