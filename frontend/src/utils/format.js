/**
 * Date helpers. The API sends plain dates as "YYYY-MM-DD". They are parsed as *local* dates on purpose:
 * `new Date("2026-10-04")` would mean midnight UTC, which shows as the previous day west of UTC.
 */

export function parseDate(isoDate) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function toIsoDate(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function todayIso() {
  return toIsoDate(new Date())
}

export function formatDate(isoDate) {
  if (!isoDate) return ''
  return parseDate(isoDate).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function formatShortDate(isoDate) {
  if (!isoDate) return ''
  return parseDate(isoDate).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

export function formatDateRange(start, end) {
  return start === end ? formatDate(start) : `${formatShortDate(start)} – ${formatDate(end)}`
}

export function formatDateTime(instant) {
  if (!instant) return ''
  return new Date(instant).toLocaleString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

export function pluralDays(count) {
  return `${count} day${count === 1 ? '' : 's'}`
}

export function titleCase(value) {
  if (!value) return ''
  return value.charAt(0) + value.slice(1).toLowerCase()
}
