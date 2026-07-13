export type MatchStatus = 'LIVE' | 'UPCOMING' | 'COMPLETED'

export interface Team {
  id: string | null
  name: string
  country: string
  score: number | null
  logo: string | null
  won: boolean | null
}

export interface Match {
  id: string
  status: MatchStatus
  teams: [Team, Team]
  event: string
  tournament: string
  event_logo: string | null
  starts_at: string | null
  countdown: string | null
  relative_time: string | null
  updated_at: string
  vlr_url: string
}

export interface Health {
  status: 'UP' | 'DEGRADED'
  upstream: 'UP' | 'DEGRADED' | 'UNKNOWN'
  database: 'UP' | 'DOWN'
  live_matches: number
  last_sync: string | null
  consecutive_failures: number
  polling_mode: 'ACTIVE' | 'IDLE'
  version: string
}

export interface Stats {
  live_matches: number
  upcoming_matches: number
  completed_matches: number
  tracked_teams: number
  active_events: number
}

export interface Dashboard {
  live: Match[]
  upcoming: Match[]
  recent: Match[]
  stats: Stats
  health: Health
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  meta: Record<string, unknown>
}
