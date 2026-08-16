import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ChevronDown, ChevronRight, MapPin, Eye } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import type { LocationTreeNode } from '../api/locationApi'

interface LocationTreeViewProps {
  tree: LocationTreeNode[]
  isLoading?: boolean
}

/**
 * LocationTreeView — read-only hierarchikus fa nézet.
 *
 * A LocationsPage toggle gombbal kapcsolhat a lapozott táblázat és ez
 * a fa nézet között. A expand/collapse state lokális (komponens-szintű).
 */
export function LocationTreeView({ tree, isLoading }: LocationTreeViewProps) {
  const { t } = useTranslation()
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set())

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

  if (isLoading) {
    return (
      <Card>
        <CardContent className="py-6">
          <p className="text-muted-foreground">{t('common.loading')}...</p>
        </CardContent>
      </Card>
    )
  }

  if (!tree || tree.length === 0) {
    return (
      <Card>
        <CardContent className="py-6">
          <p className="text-muted-foreground">{t('common.noData')}</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardContent className="space-y-1 py-4">
        {tree.map((node) => (
          <TreeRow
            key={node.id}
            node={node}
            depth={0}
            expandedIds={expandedIds}
            onToggle={toggleExpanded}
          />
        ))}
      </CardContent>
    </Card>
  )
}

interface TreeRowProps {
  node: LocationTreeNode
  depth: number
  expandedIds: Set<number>
  onToggle: (id: number) => void
}

function TreeRow({ node, depth, expandedIds, onToggle }: TreeRowProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const hasChildren = node.children.length > 0
  const isExpanded = expandedIds.has(node.id) || depth === 0

  return (
    <>
      <div
        className="flex items-center gap-1 rounded-md px-2 py-1.5 text-sm hover:bg-accent"
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
        <MapPin className="h-3.5 w-3.5 text-muted-foreground" />
        <span className="flex-1">{node.name}</span>
        <Badge variant="outline" className="text-xs">
          {typeLabel(node.type)}
        </Badge>
        {node.depth > 0 && (
          <span className="ml-2 text-xs text-muted-foreground">
            L{node.depth}
          </span>
        )}
        <Button
          variant="ghost"
          size="icon"
          className="h-6 w-6 ml-1"
          onClick={() => navigate(`/locations/${node.id}`)}
          title={t('common.details', 'Részletek')}
        >
          <Eye className="h-3.5 w-3.5 text-muted-foreground hover:text-foreground" />
        </Button>
      </div>
      {isExpanded &&
        node.children.map((child) => (
          <TreeRow
            key={child.id}
            node={child}
            depth={depth + 1}
            expandedIds={expandedIds}
            onToggle={onToggle}
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
