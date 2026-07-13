import { ArrowUpRight } from 'lucide-react'
import { fullMatchTime } from '../lib/format'
import type { Match } from '../types'
import { TeamMark } from './TeamMark'

interface FeaturedMatchProps {
  readonly match: Match
}

export function FeaturedMatch({ match }: FeaturedMatchProps) {
  const [home, away] = match.teams
  return (
    <article className="feature">
      <header className="feature__strip">
        <span className="feature__live">Live</span>
        <span className="feature__tournament" title={match.tournament}>
          {match.tournament}
        </span>
        <span className="feature__stage">{match.event}</span>
      </header>

      <div className="feature__board">
        <div className="feature__side">
          <TeamMark team={home} size="large" showCountry />
        </div>
        <div className="feature__mid">
          <p
            className="feature__score"
            aria-label={`Series score ${String(home.score ?? 0)} to ${String(away.score ?? 0)}`}
          >
            <b>{home.score ?? '–'}</b>
            <i aria-hidden="true">:</i>
            <b>{away.score ?? '–'}</b>
          </p>
          <p className="feature__when">{fullMatchTime(match.starts_at)}</p>
        </div>
        <div className="feature__side feature__side--away">
          <TeamMark team={away} size="large" showCountry />
        </div>
      </div>

      <footer className="feature__foot">
        <span>Series score</span>
        <a href={match.vlr_url} target="_blank" rel="noreferrer">
          Match page on vlr.gg <ArrowUpRight size={14} strokeWidth={2.25} />
        </a>
      </footer>
    </article>
  )
}
