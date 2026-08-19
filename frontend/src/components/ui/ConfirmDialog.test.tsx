import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  it('renders title and description when open is true', () => {
    render(
      <ConfirmDialog
        open={true}
        onOpenChange={vi.fn()}
        title="Biztosan törölni szeretnéd?"
        description="Ez a művelet nem visszavonható."
        onConfirm={vi.fn()}
      />
    )

    expect(screen.getByText('Biztosan törölni szeretnéd?')).toBeInTheDocument()
    expect(screen.getByText('Ez a művelet nem visszavonható.')).toBeInTheDocument()
  })

  it('calls onConfirm and onOpenChange(false) on confirm button click', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    const onOpenChange = vi.fn()

    render(
      <ConfirmDialog
        open={true}
        onOpenChange={onOpenChange}
        title="Törlés megerősítése"
        confirmText="Igen, törlöm"
        onConfirm={onConfirm}
      />
    )

    const confirmButton = screen.getByRole('button', { name: 'Igen, törlöm' })
    await user.click(confirmButton)

    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('calls onOpenChange(false) on cancel button click without calling onConfirm', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    const onOpenChange = vi.fn()

    render(
      <ConfirmDialog
        open={true}
        onOpenChange={onOpenChange}
        title="Törlés megerősítése"
        cancelText="Mégsem"
        onConfirm={onConfirm}
      />
    )

    const cancelButton = screen.getByRole('button', { name: 'Mégsem' })
    await user.click(cancelButton)

    expect(onConfirm).not.toHaveBeenCalled()
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('disables buttons when loading is true', () => {
    render(
      <ConfirmDialog
        open={true}
        onOpenChange={vi.fn()}
        title="Folyamatban..."
        loading={true}
        onConfirm={vi.fn()}
      />
    )

    const buttons = screen.getAllByRole('button')
    buttons.forEach((btn) => {
      expect(btn).toBeDisabled()
    })
  })
})
