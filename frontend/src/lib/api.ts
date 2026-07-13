import type { ApiResponse, Dashboard, Match } from '../types'

const API_ROOT = import.meta.env.VITE_API_ROOT ?? '/api/v1'

async function request<T>(path: string): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}`, { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`SpikeTracker API returned ${String(response.status)}`)
  const payload: unknown = await response.json()
  return payload as T
}

export async function getDashboard(): Promise<Dashboard> {
  const response = await request<ApiResponse<Dashboard>>('/dashboard')
  return response.data
}

export async function searchMatches(query: string): Promise<Match[]> {
  const params = new URLSearchParams({ query, limit: '20' })
  const response = await request<ApiResponse<Match[]>>(`/matches?${params}`)
  return response.data
}

export function websocketUrl(): string {
  const override = import.meta.env.VITE_WS_ROOT
  if (override) return `${override.replace(/\/$/, '')}/api/v1/ws`
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/api/v1/ws`
}
