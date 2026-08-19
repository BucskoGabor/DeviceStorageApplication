import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { LocationTreeView } from './LocationTreeView'
import type { LocationTreeNode } from '../api/locationApi'

const mockTree: LocationTreeNode[] = [
  {
    id: 1,
    name: 'Főépület',
    type: 'GROUP',
    parentId: null,
    depth: 0,
    children: [
      {
        id: 2,
        name: 'Tanszéki Szárny A',
        type: 'GROUP',
        parentId: 1,
        depth: 1,
        children: [
          {
            id: 3,
            name: 'Iroda 101',
            type: 'OFFICE',
            parentId: 2,
            depth: 2,
            children: [],
          },
        ],
      },
    ],
  },
]

describe('LocationTreeView', () => {
  it('renders root location node and its type badge', () => {
    render(
      <MemoryRouter>
        <LocationTreeView tree={mockTree} />
      </MemoryRouter>
    )

    expect(screen.getByText('Főépület')).toBeInTheDocument()
    expect(screen.getAllByText(/Csoport|Group/i).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('Tanszéki Szárny A')).toBeInTheDocument()
  })

  it('renders loading state when isLoading is true', () => {
    render(
      <MemoryRouter>
        <LocationTreeView tree={[]} isLoading={true} />
      </MemoryRouter>
    )

    expect(screen.getByText(/Betöltés|Loading/i)).toBeInTheDocument()
  })

  it('renders empty message when tree is empty', () => {
    render(
      <MemoryRouter>
        <LocationTreeView tree={[]} />
      </MemoryRouter>
    )

    expect(screen.getByText(/Nincs megjeleníthető adat|No data/i)).toBeInTheDocument()
  })

  it('expands nested sub-children when expand button on depth > 0 is clicked', async () => {
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <LocationTreeView tree={mockTree} />
      </MemoryRouter>
    )

    // Deep nested child (depth 2) is not visible initially
    expect(screen.queryByText('Iroda 101')).not.toBeInTheDocument()

    // Find the expand button for depth 1 ('Tanszéki Szárny A')
    const expandButtons = screen.getAllByRole('button', { name: /Expand/i })
    expect(expandButtons.length).toBeGreaterThan(0)
    await user.click(expandButtons[0]!)
    // Now deep child is visible
    expect(screen.getByText('Iroda 101')).toBeInTheDocument()
  })
})
