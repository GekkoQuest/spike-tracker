import { useState } from 'react'
import { countryCode } from '../lib/format'
import type { Team } from '../types'

interface TeamMarkProps {
  readonly team: Team
  readonly size?: 'small' | 'large'
  readonly showCountry?: boolean
}

export function TeamMark({ team, size = 'small', showCountry = false }: TeamMarkProps) {
  const [failed, setFailed] = useState(false)
  const country = countryCode(team.country)
  const initials = team.name
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0])
    .join('')
    .toUpperCase()
  return (
    <div className={`team-mark team-mark--${size}`}>
      <div className="team-mark__logo" aria-hidden="true">
        {team.logo && !failed ? (
          <img
            src={team.logo}
            alt=""
            loading="lazy"
            onError={() => {
              setFailed(true)
            }}
          />
        ) : (
          <span>{initials.length > 0 ? initials : '?'}</span>
        )}
      </div>
      <div className="team-mark__copy">
        <strong>{team.name}</strong>
        {showCountry && country && <span>{country}</span>}
      </div>
    </div>
  )
}
