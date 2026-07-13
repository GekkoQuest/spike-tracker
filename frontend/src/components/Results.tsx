import { ArrowUpRight } from 'lucide-react'
import type { Match } from '../types'
import { TeamMark } from './TeamMark'

interface ResultsProps {
  readonly matches: readonly Match[]
}

export function Results({ matches }: ResultsProps) {
  return (
    <div className="table">
      <div className="table__labels" aria-hidden="true">
        <span>Final</span>
        <span>Winner · score · opponent</span>
        <span className="table__labels-event">Event</span>
        <span />
      </div>
      <div className="table__body">
        {matches.map((match) => {
          const winner = match.teams.find((team) => team.won) ?? match.teams[0]
          const loser = winner === match.teams[0] ? match.teams[1] : match.teams[0]
          return (
            <a
              className="result-row"
              href={match.vlr_url}
              target="_blank"
              rel="noreferrer"
              key={match.id}
            >
              <span className="result-row__time">{match.relative_time ?? 'Recent'}</span>
              <div className="result-row__matchup">
                <div className="result-row__team result-row__team--winner">
                  <TeamMark team={winner} />
                </div>
                <span className="result-row__score">
                  <b>{winner.score ?? '–'}</b>
                  <i aria-hidden="true">:</i>
                  <span>{loser.score ?? '–'}</span>
                </span>
                <div className="result-row__team">
                  <TeamMark team={loser} />
                </div>
              </div>
              <span className="row-event">
                {match.tournament}
                <small>{match.event}</small>
              </span>
              <ArrowUpRight className="row-arrow" size={15} aria-hidden="true" />
            </a>
          )
        })}
      </div>
    </div>
  )
}
