import { useQuery } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { FeaturedMatch } from './components/FeaturedMatch'
import { MatchRail } from './components/MatchRail'
import { Results } from './components/Results'
import { SearchDialog } from './components/SearchDialog'
import { Upcoming } from './components/Upcoming'
import { useLiveFeed } from './hooks/useLiveFeed'
import { getDashboard } from './lib/api'
import { ago } from './lib/format'

function SpikeMark({ size = 20 }: { readonly size?: number }) {
  return (
    <svg viewBox="0 0 64 64" width={size} height={size} aria-hidden="true">
      <path d="M0 0h64v50L50 64H0Z" fill="#ff4655" />
      <path d="M15 15h11.5L32 29.5 37.5 15H49L32 50 15 15Z" fill="#ece8e1" />
    </svg>
  )
}

interface SectionHeadingProps {
  readonly count: number
  readonly detail?: string
  readonly title: string
}

function SectionHeading({ title, count, detail }: SectionHeadingProps) {
  return (
    <header className="section-head">
      <h2>
        {title}
        <span className="section-head__count">{count}</span>
      </h2>
      {detail && <p>{detail}</p>}
    </header>
  )
}

export default function App() {
  const dashboard = useQuery({
    queryKey: ['dashboard'],
    queryFn: getDashboard,
    refetchInterval: 60_000,
  })
  useLiveFeed()
  const data = dashboard.data

  if (dashboard.isLoading) {
    return (
      <div className="boot-screen">
        <SpikeMark size={32} />
        <span>Loading matches…</span>
      </div>
    )
  }

  if (dashboard.isError || !data) {
    return (
      <div className="error-screen">
        <small>Connection failed</small>
        <h1>Match data is unavailable</h1>
        <p>{dashboard.error?.message ?? 'The API could not be reached.'}</p>
        <button
          type="button"
          onClick={() => {
            void dashboard.refetch()
          }}
        >
          <RefreshCw size={14} /> Try again
        </button>
      </div>
    )
  }

  const feature = data.live[0]
  const liveCount = data.live.length

  return (
    <div className="app">
      <header className="topbar">
        <div className="shell topbar__in">
          <a className="brand" href="#live" aria-label="SpikeTracker home">
            <SpikeMark />
            <span>SpikeTracker</span>
          </a>
          <nav className="topbar__nav" aria-label="Page sections">
            <a href="#live">Live</a>
            <a href="#schedule">Schedule</a>
            <a href="#results">Results</a>
          </nav>
          <div className="topbar__side">
            <span className="live-count" data-live={liveCount > 0 ? 'true' : 'false'}>
              <i aria-hidden="true" />
              {liveCount > 0 ? `${String(liveCount)} live` : 'No live play'}
            </span>
            <SearchDialog />
          </div>
        </div>
      </header>

      <main className="shell">
        <section className="section" id="live">
          <SectionHeading title="Live matches" count={liveCount} />
          {feature ? (
            <div className="live-grid" data-solo={data.live.length === 1 ? 'true' : 'false'}>
              <FeaturedMatch match={feature} />
              {data.live.length > 1 && <MatchRail matches={data.live.slice(1)} />}
            </div>
          ) : (
            <div className="quiet-state">
              <b>No match is live.</b>
              <p>The next scheduled series is listed below.</p>
            </div>
          )}
        </section>

        <section className="section" id="schedule">
          <SectionHeading
            title="Schedule"
            count={data.stats.upcoming_matches}
            detail="Next eight matches, in your local timezone."
          />
          {data.upcoming.length ? (
            <Upcoming matches={data.upcoming} />
          ) : (
            <p className="inline-empty">No upcoming matches are listed.</p>
          )}
        </section>

        <section className="section" id="results">
          <SectionHeading title="Recent results" count={data.stats.completed_matches} />
          {data.recent.length ? (
            <Results matches={data.recent} />
          ) : (
            <p className="inline-empty">No completed matches have been indexed.</p>
          )}
        </section>
      </main>

      <footer className="foot">
        <div className="shell foot__in">
          <div className="foot__row">
            <p>Independent Valorant match index. Data supplied by VLR through vlresports.</p>
            <div className="foot__links">
              <span>Synced {ago(data.health.last_sync)}</span>
              <a
                href="https://github.com/GekkoQuest/spike-tracker"
                target="_blank"
                rel="noreferrer"
              >
                Source
              </a>
              <a href="/api/v1/docs">API</a>
              <span>v{data.health.version}</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
