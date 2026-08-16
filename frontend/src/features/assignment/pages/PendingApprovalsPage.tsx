import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ApprovalQueue } from '../components/ApprovalQueue'

/**
 * PendingApprovalsPage — /admin/approvals route.
 *
 * Az assignment workflow jóváhagyási sora. Csak DEVICE_ASSIGN permissionnel
 * rendelkező user érheti el (admin/teacher).
 */
export function PendingApprovalsPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('assignments.approvalQueue')}</h1>
      <Card>
        <CardHeader>
          <CardTitle>{t('assignments.approvalQueue')}</CardTitle>
          <CardDescription>{t('assignments.title')}</CardDescription>
        </CardHeader>
        <CardContent>
          <ApprovalQueue />
        </CardContent>
      </Card>
    </div>
  )
}
