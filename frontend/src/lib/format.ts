import { format, formatDistanceToNow, isValid, parseISO } from 'date-fns'

export function matchTime(value: string | null, fallback?: string | null): string {
  if (!value) return fallback ?? 'TBD'
  const date = parseISO(value)
  return isValid(date) ? format(date, 'EEE HH:mm') : (fallback ?? 'TBD')
}

export function fullMatchTime(value: string | null): string {
  if (!value) return 'Start time TBD'
  const date = parseISO(value)
  return isValid(date) ? format(date, 'EEE, MMM d · HH:mm') : 'Start time TBD'
}

export function ago(value: string | null): string {
  if (!value) return 'awaiting first sync'
  const date = parseISO(value)
  return isValid(date) ? `${formatDistanceToNow(date)} ago` : 'recently'
}

export function countryCode(country: string): string | null {
  const code = country.trim().toUpperCase()
  return /^[A-Z]{2}$/.test(code) && code !== 'UN' ? code : null
}
