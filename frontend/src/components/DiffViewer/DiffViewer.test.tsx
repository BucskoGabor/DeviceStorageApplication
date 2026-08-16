import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DiffViewer } from './DiffViewer'

describe('DiffViewer', () => {
  it('renders fallback when changesJson is null or empty', () => {
    render(<DiffViewer changesJson={null} />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('renders error card when JSON is malformed', () => {
    render(<DiffViewer changesJson="invalid-json" />)
    expect(screen.getByText(/invalid-json/)).toBeInTheDocument()
  })

  it('renders diff table with before and after changes correctly', () => {
    const changes = JSON.stringify({
      before: { status: 'IN_STORAGE', name: 'Dell Latitude' },
      after: { status: 'MAINTENANCE', name: 'Dell Latitude' },
    })

    render(<DiffViewer changesJson={changes} />)
    expect(screen.getByText('status')).toBeInTheDocument()
    expect(screen.getByText(/"IN_STORAGE"/)).toBeInTheDocument()
    expect(screen.getByText(/"MAINTENANCE"/)).toBeInTheDocument()
  })
})
