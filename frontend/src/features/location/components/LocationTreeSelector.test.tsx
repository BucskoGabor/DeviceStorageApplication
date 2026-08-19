import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { LocationTreeSelector, collectAncestorIds } from './LocationTreeSelector'
import type { LocationTreeNode } from '../api/locationApi'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
const { mockTree } = vi.hoisted(() => ({
  mockTree: [
    {
      id: 1,
      name: 'Főépület',
      type: 'GROUP',
      parentId: null,
      depth: 0,
      children: [
        {
          id: 2,
          name: 'Iroda 101',
          type: 'OFFICE',
          parentId: 1,
          depth: 1,
          children: [],
        },
      ],
    },
    {
      id: 3,
      name: 'Központi Raktár',
      type: 'STORAGE',
      parentId: null,
      depth: 0,
      children: [],
    },
    {
      id: 4,
      name: 'Tanterem 201',
      type: 'CLASSROOM',
      parentId: null,
      depth: 0,
      children: [],
    },
  ] as LocationTreeNode[],
}))

vi.mock('../api/locationApi', () => ({
  locationApi: {
    findTree: vi.fn().mockImplementation(() => Promise.resolve(mockTree)),
  },
  locationKeys: {
    all: ['locations'] as const,
    tree: () => ['locations', 'tree'] as const,
    detail: (id: number) => ['locations', id] as const,
  },
}))

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('LocationTreeSelector', () => {
  it('collectAncestorIds correctly finds ancestor IDs for deep selection', () => {
    const ancestors = collectAncestorIds(mockTree, 2)
    expect(ancestors.has(1)).toBe(true)
  })

  it('disables non-storage locations when onlyStorageType is true', async () => {
    const onSelect = vi.fn()
    renderWithClient(
      <LocationTreeSelector
        open={true}
        onOpenChange={() => {}}
        onSelect={onSelect}
        onlyStorageType={true}
      />
    )

    // Wait for tree items to load
    const classroomBtn = await screen.findByRole('button', { name: /Tanterem 201/i })
    expect(classroomBtn).toBeDisabled()

    const storageBtn = await screen.findByRole('button', { name: /Központi Raktár/i })
    expect(storageBtn).not.toBeDisabled()

    const user = userEvent.setup()
    await user.click(storageBtn)
    expect(onSelect).toHaveBeenCalledWith(
      3,
      expect.objectContaining({ id: 3, name: 'Központi Raktár' })
    )
  })

  it('disables group locations when excludeGroupType is true', async () => {
    const onSelect = vi.fn()
    renderWithClient(
      <LocationTreeSelector
        open={true}
        onOpenChange={() => {}}
        onSelect={onSelect}
        excludeGroupType={true}
      />
    )

    const groupBtn = await screen.findByRole('button', { name: /Főépület/i })
    expect(groupBtn).toBeDisabled()

    const classroomBtn = await screen.findByRole('button', { name: /Tanterem 201/i })
    expect(classroomBtn).not.toBeDisabled()

    const storageBtn = await screen.findByRole('button', { name: /Központi Raktár/i })
    expect(storageBtn).not.toBeDisabled()
  })
})
