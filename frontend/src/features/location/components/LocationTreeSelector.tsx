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

export interface LocationTreeSelectorProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelect: (locationId: number | null, locationNode?: LocationTreeNode | null) => void
  selectedId?: number | null
  title?: string
  description?: string
  /**
   * Ha megadva, kizárja a GROUP típusú location-öket
   * (pl. assignment célhelyszín kiválasztásához).
   */
  excludeGroupType?: boolean
  onlyStorageType?: boolean
}

/**
 * Rekurzív kereső a kiválasztott helyszín node és név megtalálásához a fában.
 */
export function findLocationNode(
  nodes: LocationTreeNode[] | undefined | null,
  id: number | null | undefined
): LocationTreeNode | null {
  if (!nodes || id == null) return null
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children && node.children.length > 0) {
      const found = findLocationNode(node.children, id)
      if (found) return found
    }
  }
  return null
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
  title,
  description,
  excludeGroupType = false,
  onlyStorageType = false,
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

  const handleSelect = (node: LocationTreeNode | null) => {
    onSelect(node ? node.id : null, node)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[80vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{title || t('locations.selectParent', 'Helyszín kiválasztása')}</DialogTitle>
          <DialogDescription>
            {description ||
              t('locations.selectParentHelp', 'Válaszd ki a megfelelő helyszínt a listából.')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-1 py-4">
          {isLoading ? (
            <p className="text-muted-foreground">{t('common.loading')}...</p>
          ) : !tree || tree.length === 0 ? (
            <p className="text-muted-foreground">{t('common.noData')}</p>
          ) : (
            <>
              {!onlyStorageType && (
                <button
                  onClick={() => handleSelect(null)}
                  className={`flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-accent ${
                    selectedId == null ? 'bg-accent font-medium' : ''
                  }`}
                >
                  <span className="w-4" />
                  <span>— {t('locations.noParent', 'Nincs kiválasztva')}</span>
                  {selectedId == null && <Check className="ml-auto h-4 w-4" />}
                </button>
              )}
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
                  onlyStorageType={onlyStorageType}
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
  onSelect: (node: LocationTreeNode | null) => void
  selectedId?: number | null
  excludeGroupType: boolean
  onlyStorageType?: boolean
}

function TreeRow({
  node,
  depth,
  expandedIds,
  onToggle,
  onSelect,
  selectedId,
  excludeGroupType,
  onlyStorageType = false,
}: TreeRowProps) {
  const hasChildren = node.children.length > 0
  const isExpanded = expandedIds.has(node.id)
  const isSelected = selectedId === node.id
  const isDisabled =
    (excludeGroupType && node.type === 'GROUP') || (onlyStorageType && node.type !== 'STORAGE')

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
          onClick={() => !isDisabled && onSelect(node)}
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
            onlyStorageType={onlyStorageType}
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
