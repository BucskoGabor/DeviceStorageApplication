import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown, ChevronRight, Check } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { locationApi, type LocationTreeNode } from '../api/locationApi'
import { useQuery } from '@tanstack/react-query'

interface LocationTreeSelectorProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelect: (locationId: number | null) => void
  selectedId?: number | null
  /**
   * Ha megadva, kizárja a GROUP típusú location-öket
   * (pl. assignment célhelyszín kiválasztásához).
   */
  excludeGroupType?: boolean
}

/**
 * LocationTreeSelector — hierarchikus helyszínválasztó Dialog-ban.
 *
 * Fa nézetben jeleníti meg a teljes hierarchiát (a /api/locations/tree endpoint alapján).
 * A user kiválaszthatja a kívánt node-ot, vagy a "—" opciót (nincs kiválasztott).
 *
 * Reusable komponens — használható az AssignmentDialog-ban és a
 * Location create/edit űrlapokban a parent kiválasztásához.
 */
export function LocationTreeSelector({
  open,
  onOpenChange,
  onSelect,
  selectedId,
  excludeGroupType = false,
}: LocationTreeSelectorProps) {
  const { t } = useTranslation()
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set())

  const { data: tree, isLoading } = useQuery({
    queryKey: ['locations', 'tree'],
    queryFn: () => locationApi.findTree(),
    enabled: open,
    staleTime: 30000,
  })

  const toggleExpanded = (id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  const handleSelect = (id: number | null) => {
    onSelect(id)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[80vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('locations.selectParent')}</DialogTitle>
          <DialogDescription>{t('locations.selectParentHelp')}</DialogDescription>
        </DialogHeader>

        <div className="space-y-1 py-4">
          {isLoading ? (
            <p className="text-muted-foreground">{t('common.loading')}...</p>
          ) : !tree || tree.length === 0 ? (
            <p className="text-muted-foreground">{t('common.noData')}</p>
          ) : (
            <>
              <button
                onClick={() => handleSelect(null)}
                className={`flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-accent ${
                  selectedId == null ? 'bg-accent font-medium' : ''
                }`}
              >
                <span className="w-4" />
                <span>— {t('locations.noParent')}</span>
                {selectedId == null && <Check className="ml-auto h-4 w-4" />}
              </button>
              {tree.map((node) => (
                <TreeRow
                  key={node.id}
                  node={node}
                  depth={0}
                  expandedIds={expandedIds}
                  onToggle={toggleExpanded}
                  onSelect={handleSelect}
                  selectedId={selectedId}
                  excludeGroupType={excludeGroupType}
                />
              ))}
            </>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

interface TreeRowProps {
  node: LocationTreeNode
  depth: number
  expandedIds: Set<number>
  onToggle: (id: number) => void
  onSelect: (id: number | null) => void
  selectedId?: number | null
  excludeGroupType: boolean
}

function TreeRow({
  node,
  depth,
  expandedIds,
  onToggle,
  onSelect,
  selectedId,
  excludeGroupType,
}: TreeRowProps) {
  const hasChildren = node.children.length > 0
  const isExpanded = expandedIds.has(node.id)
  const isSelected = selectedId === node.id
  const isDisabled = excludeGroupType && node.type === 'GROUP'

  return (
    <>
      <div
        className={`flex items-center gap-1 rounded-md px-2 py-1.5 text-sm ${
          isDisabled
            ? 'cursor-not-allowed opacity-50'
            : isSelected
              ? 'bg-accent font-medium'
              : 'hover:bg-accent'
        }`}
        style={{ paddingLeft: `${depth * 1.25 + 0.5}rem` }}
      >
        {hasChildren ? (
          <button
            onClick={() => onToggle(node.id)}
            className="flex h-4 w-4 items-center justify-center"
            aria-label={isExpanded ? 'Collapse' : 'Expand'}
          >
            {isExpanded ? (
              <ChevronDown className="h-3 w-3" />
            ) : (
              <ChevronRight className="h-3 w-3" />
            )}
          </button>
        ) : (
          <span className="w-4" />
        )}
        <button
          onClick={() => !isDisabled && onSelect(node.id)}
          disabled={isDisabled}
          className="flex flex-1 items-center gap-2 text-left disabled:cursor-not-allowed"
        >
          <span className="flex-1">{node.name}</span>
          <Badge variant="outline" className="text-xs">
            {typeLabel(node.type)}
          </Badge>
          {isSelected && <Check className="ml-1 h-4 w-4" />}
        </button>
      </div>
      {isExpanded &&
        node.children.map((child) => (
          <TreeRow
            key={child.id}
            node={child}
            depth={depth + 1}
            expandedIds={expandedIds}
            onToggle={onToggle}
            onSelect={onSelect}
            selectedId={selectedId}
            excludeGroupType={excludeGroupType}
          />
        ))}
    </>
  )
}

function typeLabel(type: LocationTreeNode['type']): string {
  switch (type) {
    case 'OFFICE':
      return 'Iroda'
    case 'CLASSROOM':
      return 'Tanterem'
    case 'STORAGE':
      return 'Raktár'
    case 'GROUP':
      return 'Csoport'
    default:
      return type
  }
}
