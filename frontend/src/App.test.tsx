// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { expect, test, vi } from 'vitest'
import App from './App'

class MockWebSocket {
  onopen = null
  onmessage = null
  onclose = null

  close() {
    return undefined
  }
}
vi.stubGlobal('WebSocket', MockWebSocket)
vi.stubGlobal(
  'fetch',
  vi.fn().mockResolvedValue({
    ok: true,
    json: () =>
      Promise.resolve({
        success: true,
        data: {
          live: [],
          upcoming: [],
          recent: [],
          stats: {
            live_matches: 0,
            upcoming_matches: 0,
            completed_matches: 0,
            tracked_teams: 0,
            active_events: 0,
          },
          health: {
            status: 'UP',
            upstream: 'UP',
            database: 'UP',
            live_matches: 0,
            last_sync: null,
            consecutive_failures: 0,
            polling_mode: 'IDLE',
            version: '2.0.0',
          },
        },
      }),
  }),
)

test('renders the match desk', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
  expect(await screen.findByText('Live matches')).toBeInTheDocument()
  expect(screen.getByText('No match is live.')).toBeInTheDocument()
})
