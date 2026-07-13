import { useEffect, useRef, useState, type SyntheticEvent } from 'react'
import { ArrowUpRight, Search, X } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { searchMatches } from '../lib/api'

const STATUS_LABELS = { LIVE: 'Live', UPCOMING: 'Next', COMPLETED: 'Final' } as const

export function SearchDialog() {
  const dialog = useRef<HTMLDialogElement>(null)
  const [query, setQuery] = useState('')
  const [submitted, setSubmitted] = useState('')
  const searchResults = useQuery({
    queryKey: ['search', submitted],
    queryFn: () => searchMatches(submitted),
    enabled: submitted.length >= 2,
  })

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.key === '/' && document.activeElement?.tagName !== 'INPUT') {
        event.preventDefault()
        dialog.current?.showModal()
      }
    }
    window.addEventListener('keydown', handler)
    return () => {
      window.removeEventListener('keydown', handler)
    }
  }, [])

  function handleSubmit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    if (query.trim().length >= 2) setSubmitted(query.trim())
  }

  return (
    <>
      <button
        className="search-trigger"
        type="button"
        onClick={() => {
          dialog.current?.showModal()
        }}
      >
        <Search size={14} strokeWidth={2.25} aria-hidden="true" />
        <span>Search</span>
        <kbd aria-hidden="true">/</kbd>
      </button>
      <dialog
        className="search-dialog"
        ref={dialog}
        onClick={(event) => {
          if (event.target === dialog.current) dialog.current.close()
        }}
      >
        <header className="search-dialog__strip">
          <span>Find a match</span>
          <button
            type="button"
            onClick={() => {
              dialog.current?.close()
            }}
            aria-label="Close search"
          >
            <X size={16} strokeWidth={2.5} />
          </button>
        </header>
        <form className="search-dialog__bar" onSubmit={handleSubmit}>
          <Search size={16} aria-hidden="true" />
          <input
            autoFocus
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
            }}
            placeholder="Team, event, or tournament…"
            aria-label="Search matches"
          />
          <button type="submit">Search</button>
        </form>
        <div className="search-dialog__results">
          {!submitted && <p>Searches the local match index. Two characters minimum.</p>}
          {searchResults.isFetching && <p>Searching…</p>}
          {searchResults.data?.map((match) => (
            <a key={match.id} href={match.vlr_url} target="_blank" rel="noreferrer">
              <span className={`status-tag status-tag--${match.status.toLowerCase()}`}>
                {STATUS_LABELS[match.status]}
              </span>
              <b>
                {match.teams[0].name} <i>vs</i> {match.teams[1].name}
              </b>
              <small>{match.tournament}</small>
              <ArrowUpRight className="row-arrow" size={15} aria-hidden="true" />
            </a>
          ))}
          {submitted && !searchResults.isFetching && searchResults.data?.length === 0 && (
            <p>No matches found for “{submitted}”.</p>
          )}
        </div>
      </dialog>
    </>
  )
}
