import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { websocketUrl } from '../lib/api'
import type { Dashboard, Match } from '../types'

type FeedState = 'connecting' | 'live' | 'retrying'

interface MatchUpdate {
  data: {
    live: Match[]
    upcoming: Match[]
  }
  type: string
}

function isMatchUpdate(value: unknown): value is MatchUpdate {
  if (typeof value !== 'object' || value === null) return false
  if (!('type' in value) || typeof value.type !== 'string') return false
  if (!('data' in value) || typeof value.data !== 'object' || value.data === null) return false
  return (
    'live' in value.data &&
    Array.isArray(value.data.live) &&
    'upcoming' in value.data &&
    Array.isArray(value.data.upcoming)
  )
}

export function useLiveFeed(): FeedState {
  const queryClient = useQueryClient()
  const [state, setState] = useState<FeedState>('connecting')

  useEffect(() => {
    let socket: WebSocket | undefined
    let retryTimer: number | undefined
    let retryAttempts = 0
    let isDisposed = false

    const connect = () => {
      if (isDisposed) return
      setState(retryAttempts > 0 ? 'retrying' : 'connecting')
      socket = new WebSocket(websocketUrl())
      socket.onopen = () => {
        retryAttempts = 0
        setState('live')
      }
      socket.onmessage = (event) => {
        if (typeof event.data !== 'string') return
        const message: unknown = JSON.parse(event.data)
        if (!isMatchUpdate(message) || !message.type.startsWith('matches.')) return
        const { live, upcoming } = message.data
        queryClient.setQueryData<Dashboard>(['dashboard'], (current) =>
          current
            ? {
                ...current,
                live,
                upcoming,
                stats: {
                  ...current.stats,
                  live_matches: live.length,
                },
              }
            : current,
        )
      }
      socket.onclose = () => {
        if (isDisposed) return
        retryAttempts += 1
        setState('retrying')
        retryTimer = window.setTimeout(connect, Math.min(30_000, 1000 * 2 ** retryAttempts))
      }
    }

    connect()
    return () => {
      isDisposed = true
      socket?.close()
      if (retryTimer !== undefined) window.clearTimeout(retryTimer)
    }
  }, [queryClient])

  return state
}
