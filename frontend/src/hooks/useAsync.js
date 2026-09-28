import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage } from '../utils/errors'

/**
 * Loads data when the component mounts and whenever `deps` change.
 * Returns { data, loading, error, reload }.
 *
 * Example: const { data, loading } = useAsync(() => leaveApi.mine({ year }), [year])
 *
 * While new data loads, the previous data stays on screen (no flicker).
 * `cancelled` stops a slow, outdated response from overwriting a newer one.
 */
export default function useAsync(loader, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let cancelled = false
    loader()
      .then((data) => {
        if (!cancelled) setState({ data, loading: false, error: null })
      })
      .catch((error) => {
        if (!cancelled) setState({ data: null, loading: false, error: getErrorMessage(error) })
      })
    return () => {
      cancelled = true
    }
    // The caller decides when to reload through `deps`, like useEffect itself.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version])

  const reload = useCallback(() => {
    setState((previous) => ({ ...previous, loading: true }))
    setVersion((v) => v + 1)
  }, [])

  return { ...state, reload }
}
