import { ArrowUpRight } from 'lucide-react'
import { matchTime } from '../lib/format'
import type { Match } from '../types'

interface UpcomingProps {
  readonly matches: readonly Match[]
}

export function Upcoming({ matches }: UpcomingProps) {
  return (
    <div className="table">
      <div className="table__labels" aria-hidden="true">
        <span>Time</span>
        <span>Match</span>
        <span className="table__labels-event">Event</span>
        <span />
      </div>
      <ol className="table__body">
        {matches.map((match) => (
          <li key={match.id}>
            <a className="schedule-row" href={match.vlr_url} target="_blank" rel="noreferrer">
              <time className="schedule-row__time">
                {matchTime(match.starts_at, match.countdown)}
              </time>
              <span className="schedule-row__teams">
                <b>{match.teams[0].name}</b>
                <i>vs</i>
                <b>{match.teams[1].name}</b>
              </span>
              <span className="row-event">
                {match.tournament}
                <small>{match.event}</small>
              </span>
              <ArrowUpRight className="row-arrow" size={15} aria-hidden="true" />
            </a>
          </li>
        ))}
      </ol>
    </div>
  )
}
