import type { Match } from '../types'
import { TeamMark } from './TeamMark'

interface MatchRailProps {
  readonly matches: readonly Match[]
}

export function MatchRail({ matches }: MatchRailProps) {
  return (
    <aside className="live-rail" aria-label="More live matches">
      <p className="live-rail__title">Also live</p>
      {matches.map((match) => (
        <a
          className="live-item"
          href={match.vlr_url}
          target="_blank"
          rel="noreferrer"
          key={match.id}
        >
          <p className="live-item__event" title={match.tournament}>
            {match.tournament}
          </p>
          {match.teams.map((team, index) => (
            <div className="live-item__row" key={`${match.id}-${String(index)}`}>
              <TeamMark team={team} />
              <b>{team.score ?? '–'}</b>
            </div>
          ))}
        </a>
      ))}
    </aside>
  )
}
