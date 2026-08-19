import { useState, useEffect, useMemo } from 'react'
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
import { locationKeys } from '@/lib/api/queryKeys'

export interface LocationTreeSelectorProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelect: (id: number | null, node: LocationTreeNode | null) => void
  selectedId?: number | null
  title?: string
  description?: string
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
    if (node.children) {
      const found = findLocationNode(node.children, id)
      if (found) return found
    }
  }
  return null
}

/**
 * Összegyűjti a {@code targetId} node-ig vezető útvonal összes ős ID-ját.
 *
 * <p>Ha a {@code targetId} nem található a fában, üres Set-et ad vissza.
 * A path argumentum a rekurzió belső állapota — a hívó ne adja meg.
 *
 * <p>Példa: a fa [{id:1, children:[{id:2, children:[{id:3}]}]}, targetId=3]
 * → Set {1, 2} (a 3-as node NEM kerül bele, csak az ősei).
 */
export function collectAncestorIds(
  nodes: LocationTreeNode[],
  targetId: number,
  path: number[] = []
): Set<number> {
  for (const node of nodes) {
    if (node.id === targetId) {
      return new Set(path)
    }
    if (node.children && node.children.length > 0) {
      const found = collectAncestorIds(node.children, targetId, [...path, node.id])
      if (found.size > 0 || node.children.some((c) => c.id === targetId)) {
        return found
      }
    }
  }
  return new Set()
}

/**
 * LocationTreeSelector — hierarchikus helyszínválasztó Dialog-ban.
 *
 * <p>A {@code selectedId} prop-pal megadott kiválasztott node-ot a dialog
 * megnyitásakor automatikusan megkeressük a fában, és az összes ős node-ot
 * hozzáadjuk az {@code expandedIds} halmazhoz — így a user azonnal látja a
 * kiválasztott elemet, nem kell kézzel kibontania a fastruktúrát.
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
    queryKey: locationKeys.tree(),
    queryFn: () => locationApi.findTree(),
    enabled: open,
  })

  // Ha van selectedId, a dialog megnyitásakor automatikusan kibontjuk a kiválasztott
  // node összes ősét, hogy a user azonnal lássa a kijelölt elemet.
  const initialExpanded = useMemo(() => {
    if (!selectedId || !tree) return new Set<number>()
    return collectAncestorIds(tree, selectedId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tree, selectedId])

  useEffect(() => {
    if (initialExpanded.size > 0) {
      setExpandedIds((prev) => {
        const next = new Set(prev)
        initialExpanded.forEach((id) => next.add(id))
        return next
      })
    }
  }, [initialExpanded])

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
  const isExpanded = expandedIds.has(node.id)
  const isSelected = selectedId === node.id
  const isSelectable =
    (!excludeGroupType || node.type !== 'GROUP') && (!onlyStorageType || node.type === 'STORAGE')
  const hasChildren = node.children && node.children.length > 0

  return (
    <div>
      <div
        className={`flex items-center gap-1 rounded-md hover:bg-accent ${
          isSelected ? 'bg-accent font-medium' : ''
        }`}
        style={{ paddingLeft: `${depth * 1.25}rem` }}
      >
        {hasChildren ? (
          <button
            type="button"
            onClick={() => onToggle(node.id)}
            className="rounded p-1 hover:bg-accent-foreground/10"
            aria-label={isExpanded ? 'Collapse' : 'Expand'}
          >
            {isExpanded ? (
              <ChevronDown className="h-3.5 w-3.5" />
            ) : (
              <ChevronRight className="h-3.5 w-3.5" />
            )}
          </button>
        ) : (
          <span className="inline-block w-5" />
        )}
        <button
          type="button"
          onClick={() => isSelectable && onSelect(node)}
          disabled={!isSelectable}
          className={`flex flex-1 items-center gap-2 px-2 py-1.5 text-left text-sm ${
            isSelectable ? 'cursor-pointer' : 'cursor-not-allowed opacity-50'
          }`}
        >
          <span className="flex-1">{node.name}</span>
          <Badge variant="outline" className="text-[10px]">
            {typeLabel(node.type)}
          </Badge>
          {isSelected && <Check className="ml-2 h-4 w-4" />}
        </button>
      </div>
      {isExpanded && hasChildren && (
        <div>
          {node.children!.map((child) => (
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
        </div>
      )}
    </div>
  )
}

function typeLabel(type: LocationTreeNode['type']): string {
  switch (type) {
    case 'OFFICE':
      return 'Iroda'
    case 'STORAGE':
      return 'Raktár'
    case 'CLASSROOM':
      return 'Tanterem'
    case 'GROUP':
      return 'Csoport'
    default:
      return type
  }
}
