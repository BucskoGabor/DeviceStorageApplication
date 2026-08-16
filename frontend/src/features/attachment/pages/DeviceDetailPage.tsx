import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useDropzone } from 'react-dropzone'
import { useParams, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import {
  Upload,
  Trash2,
  FileText,
  Paperclip,
  ArrowRightCircle,
  ArrowLeftCircle,
  ArrowLeft,
  Plus,
  Key,
  Eye,
  Download,
  Wrench,
  RotateCcw,
  Check,
  X,
  PackageX,
} from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import {
  attachmentApi,
  formatFileSize,
  downloadUrl,
  previewUrl,
  canPreview,
  type DeviceAttachment,
} from '@/features/attachment/api/attachmentApi'
import { deviceApi } from '@/features/device/api/deviceApi'
import { softwareApi } from '@/features/software/api/softwareApi'
import { assignmentApi } from '@/features/assignment/api/assignmentApi'
import { useAuthStore } from '@/lib/store/authStore'
import { AssignmentDialog } from '@/features/assignment/components/AssignmentDialog'
import { UnassignDialog } from '@/features/assignment/components/UnassignDialog'
import { AssignmentHistoryTable } from '@/features/assignment/components/AssignmentHistoryTable'
import { StatusBadge } from '@/features/assignment/components/StatusBadge'

import { resolveToastMessage } from '@/lib/utils/toastUtils'

/**
 * DeviceDetailPage — admin/devices/{id} oldal.
 */
export function DeviceDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const navigate = useNavigate()
  const deviceId = Number(params.id)
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)

  const [assignDialogOpen, setAssignDialogOpen] = useState(false)
  const [unassignDialogOpen, setUnassignDialogOpen] = useState(false)
  const [maintenanceDialogOpen, setMaintenanceDialogOpen] = useState(false)
  const [maintenanceReason, setMaintenanceReason] = useState('')
  const [disposeDialogOpen, setDisposeDialogOpen] = useState(false)
  const [disposeReason, setDisposeReason] = useState('')
  const [deleteDeviceDialogOpen, setDeleteDeviceDialogOpen] = useState(false)
  const [detachSoftwareId, setDetachSoftwareId] = useState<number | null>(null)
  const [deleteAttachmentId, setDeleteAttachmentId] = useState<number | null>(null)

  const canAssign = permissions.includes('DEVICE_ASSIGN')
  const canUnassign = permissions.includes('DEVICE_UNASSIGN')
  const canUpdateDevice = permissions.includes('DEVICE_UPDATE')
  const canDeleteDevice = permissions.includes('DEVICE_DELETE')
  const canManageAttachments = permissions.includes('ATTACHMENT_MANAGE')
  const canViewLicenseKey = permissions.includes('SOFTWARE_LICENSE_VIEW')
  const canRequestMaintenance = permissions.includes('DEVICE_MAINTENANCE_REQUEST')
  const canApproveMaintenance = permissions.includes('DEVICE_MAINTENANCE_APPROVE')
  const canRequestDisposal = permissions.includes('DEVICE_DISPOSE_REQUEST')
  const canApproveDisposal = permissions.includes('DEVICE_DISPOSE_APPROVE')

  const [softwareDialogOpen, setSoftwareDialogOpen] = useState(false)
  const [previewingAttachment, setPreviewingAttachment] = useState<DeviceAttachment | null>(null)
  const [uploadProgress, setUploadProgress] = useState<number>(0)
  const [uploadingFileName, setUploadingFileName] = useState<string | null>(null)

  const { data: device } = useQuery({
    queryKey: ['device', deviceId],
    queryFn: () => deviceApi.findById(deviceId),
  })

  const { data: assignmentsData } = useQuery({
    queryKey: ['assignments', deviceId],
    queryFn: () => assignmentApi.findAssignmentsByDevice(deviceId, { page: 0, size: 50 }),
  })

  const currentAssignment = assignmentsData?.content.find(
    (a) =>
      a.status === 'ASSIGNED' ||
      a.status === 'PENDING_UNASSIGNMENT' ||
      a.status === 'PENDING_ASSIGNMENT'
  )

  const requestMaintenanceMutation = useMutation({
    mutationFn: (reason: string) => deviceApi.requestMaintenance(deviceId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      queryClient.invalidateQueries({ queryKey: ['pending-maintenance'] })
      setMaintenanceDialogOpen(false)
      setMaintenanceReason('')
      toast.success(
        t('devices.requestMaintenanceSuccess', 'Karbantartási kérelem sikeresen elküldve')
      )
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const approveMaintenanceMutation = useMutation({
    mutationFn: () => deviceApi.approveMaintenance(deviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      queryClient.invalidateQueries({ queryKey: ['pending-maintenance'] })
      queryClient.invalidateQueries({ queryKey: ['assignments', deviceId] })
      toast.success(t('devices.approveMaintenanceSuccess', 'Karbantartási kérelem jóváhagyva'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const rejectMaintenanceMutation = useMutation({
    mutationFn: () => deviceApi.rejectMaintenance(deviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      queryClient.invalidateQueries({ queryKey: ['pending-maintenance'] })
      toast.success(t('devices.rejectMaintenanceSuccess', 'Karbantartási kérelem elutasítva'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const returnFromMaintenanceMutation = useMutation({
    mutationFn: () => deviceApi.returnFromMaintenance(deviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      toast.success(
        t('devices.returnedFromMaintenanceSuccess', 'Eszköz visszavéve karbantartásból')
      )
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const requestDisposalMutation = useMutation({
    mutationFn: (reason: string) => deviceApi.requestDisposal(deviceId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      queryClient.invalidateQueries({ queryKey: ['pending-disposal'] })
      setDisposeDialogOpen(false)
      setDisposeReason('')
      toast.success(t('devices.requestDisposalSuccess', 'Selejtezési kérelem sikeresen elküldve'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const approveDisposalMutation = useMutation({
    mutationFn: () => deviceApi.approveDisposal(deviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      queryClient.invalidateQueries({ queryKey: ['pending-disposal'] })
      toast.success(t('devices.approveDisposalSuccess', 'Selejtezési kérelem jóváhagyva'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const rejectDisposalMutation = useMutation({
    mutationFn: () => deviceApi.rejectDisposal(deviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      queryClient.invalidateQueries({ queryKey: ['pending-disposal'] })
      toast.success(t('devices.rejectDisposalSuccess', 'Selejtezési kérelem elutasítva'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const deleteDeviceMutation = useMutation({
    mutationFn: () => deviceApi.delete(deviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      toast.success(t('common.deleted', 'Eszköz sikeresen törölve'))
      navigate('/devices')
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const { data: attachments, isLoading } = useQuery({
    queryKey: ['attachments', deviceId],
    queryFn: () => attachmentApi.findByDevice(deviceId),
  })

  const { data: deviceSoftware, isLoading: deviceSoftwareLoading } = useQuery({
    queryKey: ['device-software', deviceId],
    queryFn: () => deviceApi.findSoftwareByDevice(deviceId),
  })

  const detachMutation = useMutation({
    mutationFn: (softwareId: number) => deviceApi.detachSoftware(deviceId, softwareId),
    onSuccess: (_data, softwareId) => {
      queryClient.invalidateQueries({ queryKey: ['device-software', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['software-devices', softwareId] })
      toast.success(t('common.success'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const uploadMutation = useMutation({
    mutationFn: (file: File) =>
      attachmentApi.upload(deviceId, file, (progressEvent) => {
        const pct = attachmentApi.calculateProgress(progressEvent.loaded, progressEvent.total ?? 0)
        setUploadProgress(pct)
      }),
    onMutate: (file) => {
      setUploadingFileName(file.name)
      setUploadProgress(0)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attachments', deviceId] })
      toast.success(t('attachments.uploadSuccess'))
      setUploadProgress(0)
      setUploadingFileName(null)
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
      setUploadProgress(0)
      setUploadingFileName(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => attachmentApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attachments', deviceId] })
      toast.success(t('common.success'))
    },
  })

  const onDrop = (files: File[]) => {
    if (!canManageAttachments || files.length === 0 || !files[0]) return
    uploadMutation.mutate(files[0])
  }

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'image/*': ['.jpg', '.jpeg', '.png', '.gif', '.webp'],
      'application/pdf': ['.pdf'],
      'application/msword': ['.doc'],
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'],
      'application/vnd.ms-excel': ['.xls'],
      'text/plain': ['.txt'],
      'application/zip': ['.zip'],
    },
    maxSize: 10 * 1024 * 1024,
    disabled: !canManageAttachments,
  })

  return (
    <div className="space-y-6">
      <div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate('/devices')}
          className="-ml-2 mb-2 text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t('devices.title', 'Eszközök')}
        </Button>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">
            {t('devices.title')} #{deviceId}
          </h1>
          {device && (
            <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
              <span>{device.inventoryNumber}</span>
              <span>•</span>
              <span>{device.type}</span>
              <span>•</span>
              <StatusBadge status={device.status as any} />
            </div>
          )}
        </div>

        {/* Karbantartás, Selejtezés és Törlés műveleti gombok */}
        {device && (
          <div className="flex flex-wrap items-center gap-2">
            {(device.status === 'IN_STORAGE' || device.status === 'ASSIGNED') &&
              canRequestMaintenance && (
                <Button variant="outline" size="sm" onClick={() => setMaintenanceDialogOpen(true)}>
                  <Wrench className="mr-1.5 h-4 w-4 text-amber-600" />
                  {t('devices.requestMaintenance', 'Karbantartás kérése')}
                </Button>
              )}

            {device.status === 'MAINTENANCE' && canUpdateDevice && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => returnFromMaintenanceMutation.mutate()}
                disabled={returnFromMaintenanceMutation.isPending}
              >
                <RotateCcw className="mr-1.5 h-4 w-4 text-emerald-600" />
                {t('devices.returnFromMaintenance', 'Visszavétel raktárba')}
              </Button>
            )}

            {(device.status === 'IN_STORAGE' || device.status === 'MAINTENANCE') &&
              canRequestDisposal && (
                <Button variant="outline" size="sm" onClick={() => setDisposeDialogOpen(true)}>
                  <PackageX className="mr-1.5 h-4 w-4 text-rose-600" />
                  {t('devices.requestDisposal', 'Selejtezés kérése')}
                </Button>
              )}

            {canDeleteDevice && device.status === 'DISPOSED' && (
              <Button
                variant="destructive"
                size="sm"
                onClick={() => setDeleteDeviceDialogOpen(true)}
              >
                <Trash2 className="mr-1.5 h-4 w-4" />
                {t('common.delete', 'Törlés')}
              </Button>
            )}
          </div>
        )}
      </div>

      {/* Karbantartási kérelem jóváhagyási banner */}
      {device?.status === 'PENDING_MAINTENANCE' && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-500/40 bg-amber-500/10 p-4 text-sm">
          <div className="flex items-start gap-3">
            <Wrench className="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" />
            <div>
              <span className="block font-semibold text-amber-900 dark:text-amber-200">
                {t(
                  'devices.pendingMaintenanceBannerTitle',
                  'Karbantartási kérelem jóváhagyásra vár'
                )}
              </span>
              {device.statusReason && (
                <p className="mt-1 italic text-muted-foreground">"{device.statusReason}"</p>
              )}
            </div>
          </div>
          {canApproveMaintenance && (
            <div className="flex items-center gap-2">
              <Button
                size="sm"
                disabled={
                  approveMaintenanceMutation.isPending || rejectMaintenanceMutation.isPending
                }
                onClick={() => approveMaintenanceMutation.mutate()}
              >
                <Check className="mr-1 h-4 w-4" />
                {t('devices.approveMaintenance', 'Jóváhagyás')}
              </Button>
              <Button
                size="sm"
                variant="destructive"
                disabled={
                  approveMaintenanceMutation.isPending || rejectMaintenanceMutation.isPending
                }
                onClick={() => rejectMaintenanceMutation.mutate()}
              >
                <X className="mr-1 h-4 w-4" />
                {t('devices.rejectMaintenance', 'Elutasítás')}
              </Button>
            </div>
          )}
        </div>
      )}

      {/* Selejtezési kérelem jóváhagyási banner */}
      {device?.status === 'PENDING_DISPOSAL' && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-rose-500/40 bg-rose-500/10 p-4 text-sm">
          <div className="flex items-start gap-3">
            <Trash2 className="mt-0.5 h-5 w-5 shrink-0 text-rose-600 dark:text-rose-400" />
            <div>
              <span className="block font-semibold text-rose-900 dark:text-rose-200">
                {t('devices.pendingDisposalBannerTitle', 'Selejtezési kérelem jóváhagyásra vár')}
              </span>
              {device.statusReason && (
                <p className="mt-1 italic text-muted-foreground">"{device.statusReason}"</p>
              )}
            </div>
          </div>
          {canApproveDisposal && (
            <div className="flex items-center gap-2">
              <Button
                size="sm"
                disabled={approveDisposalMutation.isPending || rejectDisposalMutation.isPending}
                onClick={() => approveDisposalMutation.mutate()}
              >
                <Check className="mr-1 h-4 w-4" />
                {t('devices.approveDisposal', 'Jóváhagyás')}
              </Button>
              <Button
                size="sm"
                variant="destructive"
                disabled={approveDisposalMutation.isPending || rejectDisposalMutation.isPending}
                onClick={() => rejectDisposalMutation.mutate()}
              >
                <X className="mr-1 h-4 w-4" />
                {t('devices.rejectDisposal', 'Elutasítás')}
              </Button>
            </div>
          )}
        </div>
      )}

      {/* Indoklás megjelenítése egyéb státusz esetén (pl. már karbantartás alatt vagy már selejtezve) */}
      {device?.statusReason &&
        device.status !== 'PENDING_MAINTENANCE' &&
        device.status !== 'PENDING_DISPOSAL' && (
          <div className="flex items-start gap-3 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4 text-sm">
            <Wrench className="mt-0.5 h-5 w-5 shrink-0 text-amber-500" />
            <div>
              <span className="block font-semibold text-foreground">
                {device.status === 'MAINTENANCE'
                  ? t('devices.maintenanceReason', 'Karbantartás indoka')
                  : device.status === 'DISPOSED'
                    ? t('devices.disposalReason', 'Selejtezés indoka')
                    : t('devices.statusReason', 'Státusz indoklás')}
              </span>
              <p className="mt-1 italic text-muted-foreground">"{device.statusReason}"</p>
            </div>
          </div>
        )}

      {/* Aktuális hozzárendelés */}
      <Card>
        <CardHeader>
          <CardTitle>{t('assignments.currentAssignment')}</CardTitle>
        </CardHeader>
        <CardContent>
          {currentAssignment ? (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-3">
                <StatusBadge status={currentAssignment.status} />
                <span className="text-sm">
                  <strong>{t('assignments.toLocation')}:</strong>{' '}
                  {currentAssignment.toLocation?.name ?? '—'}
                </span>
                <span className="text-sm">
                  <strong>{t('assignments.toUser')}:</strong>{' '}
                  {currentAssignment.toUser?.email ?? '—'}
                </span>
              </div>
              <div className="flex gap-2">
                {canAssign && device?.status === 'IN_STORAGE' && (
                  <Button variant="outline" size="sm" onClick={() => setAssignDialogOpen(true)}>
                    <ArrowRightCircle className="mr-1 h-4 w-4" />
                    {t('devices.assign')}
                  </Button>
                )}
                {canUnassign && device?.status === 'ASSIGNED' && (
                  <Button variant="outline" size="sm" onClick={() => setUnassignDialogOpen(true)}>
                    <ArrowLeftCircle className="mr-1 h-4 w-4" />
                    {t('devices.unassign')}
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">{t('assignments.noActiveAssignment')}</p>
              {canAssign && device?.status === 'IN_STORAGE' && (
                <Button variant="outline" size="sm" onClick={() => setAssignDialogOpen(true)}>
                  <ArrowRightCircle className="mr-1 h-4 w-4" />
                  {t('devices.assign')}
                </Button>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <AssignmentDialog
        open={assignDialogOpen}
        onOpenChange={setAssignDialogOpen}
        deviceId={deviceId}
      />

      {currentAssignment && (
        <UnassignDialog
          open={unassignDialogOpen}
          onOpenChange={setUnassignDialogOpen}
          deviceId={deviceId}
          assignmentId={currentAssignment.id}
        />
      )}

      {/* Karbantartás kérése Dialog */}
      <Dialog open={maintenanceDialogOpen} onOpenChange={setMaintenanceDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('devices.requestMaintenance', 'Karbantartás kérése')}</DialogTitle>
            <DialogDescription>
              {device?.inventoryNumber} #{deviceId} —{' '}
              {t(
                'devices.requestMaintenanceDesc',
                'Kérelem benyújtása karbantartásra indoklással.'
              )}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <Label htmlFor="maint-reason" className="text-xs text-muted-foreground">
                {t('devices.maintenanceReason', 'Karbantartás indoka')}
              </Label>
              <Input
                id="maint-reason"
                value={maintenanceReason}
                onChange={(e) => setMaintenanceReason(e.target.value)}
                placeholder={t(
                  'devices.maintenanceReasonPlaceholder',
                  'pl. kijelző hiba, akkumulátor csere'
                )}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setMaintenanceDialogOpen(false)}
              disabled={requestMaintenanceMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              onClick={() => requestMaintenanceMutation.mutate(maintenanceReason)}
              disabled={requestMaintenanceMutation.isPending}
            >
              {t('devices.submitRequest', 'Kérelem elküldése')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Selejtezés kérése Dialog */}
      <Dialog open={disposeDialogOpen} onOpenChange={setDisposeDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('devices.requestDisposal', 'Selejtezés kérése')}</DialogTitle>
            <DialogDescription>
              {t(
                'devices.requestDisposalDesc',
                'Figyelem: A jóváhagyott selejtezés végleges állapotot eredményez.'
              )}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <Label htmlFor="dispose-reason" className="text-xs text-muted-foreground">
                {t('devices.disposeReason', 'Selejtezés indoka')}
              </Label>
              <Input
                id="dispose-reason"
                value={disposeReason}
                onChange={(e) => setDisposeReason(e.target.value)}
                placeholder={t('devices.disposeReasonPlaceholder', 'pl. gazdaságtalanul javítható')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDisposeDialogOpen(false)}
              disabled={requestDisposalMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              variant="destructive"
              onClick={() => requestDisposalMutation.mutate(disposeReason)}
              disabled={requestDisposalMutation.isPending}
            >
              {t('devices.submitRequest', 'Kérelem elküldése')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Assignment history */}
      <Card>
        <CardHeader>
          <CardTitle>{t('assignments.history')}</CardTitle>
        </CardHeader>
        <CardContent>
          <AssignmentHistoryTable deviceId={deviceId} />
        </CardContent>
      </Card>

      {/* Telepített szoftverek */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle>{t('devices.softwares')}</CardTitle>
            <CardDescription>{deviceSoftware?.length ?? 0} szoftver</CardDescription>
          </div>
          {canUpdateDevice && (
            <Button size="sm" variant="outline" onClick={() => setSoftwareDialogOpen(true)}>
              <Plus className="mr-1 h-4 w-4" />
              {t('devices.addSoftware')}
            </Button>
          )}
        </CardHeader>
        <CardContent>
          {deviceSoftwareLoading ? (
            <p className="text-muted-foreground">{t('common.loading')}...</p>
          ) : !deviceSoftware || deviceSoftware.length === 0 ? (
            <p className="text-muted-foreground">{t('common.noData')}</p>
          ) : (
            <ul className="space-y-2">
              {deviceSoftware.map((sw) => (
                <li
                  key={sw.id}
                  className="flex items-center justify-between rounded-md border border-border bg-card p-3"
                >
                  <div className="flex items-center gap-3">
                    <Key className="h-5 w-5 text-muted-foreground" />
                    <div>
                      <p className="text-sm font-medium">{sw.name}</p>
                      <p className="font-mono text-xs text-muted-foreground">
                        {canViewLicenseKey
                          ? (sw.licenseKey ?? sw.licenseKeyMasked ?? '—')
                          : (sw.licenseKeyMasked ?? '••••••••••••')}
                      </p>
                    </div>
                  </div>
                  {canUpdateDevice && (
                    <Button
                      variant="ghost"
                      size="icon"
                      disabled={detachMutation.isPending}
                      onClick={() => setDetachSoftwareId(sw.id)}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <AttachSoftwareDialog
        open={softwareDialogOpen}
        onOpenChange={setSoftwareDialogOpen}
        deviceId={deviceId}
      />

      <AttachmentPreviewDialog
        attachment={previewingAttachment}
        onOpenChange={(open) => !open && setPreviewingAttachment(null)}
      />

      <ConfirmDialog
        open={detachSoftwareId !== null}
        onOpenChange={(open) => !open && setDetachSoftwareId(null)}
        description={t(
          'devices.confirmDetachSoftware',
          'Biztosan eltávolítod ezt a szoftvert az eszközről?'
        )}
        onConfirm={() => {
          if (detachSoftwareId) {
            detachMutation.mutate(detachSoftwareId)
            setDetachSoftwareId(null)
          }
        }}
      />
      <ConfirmDialog
        open={deleteAttachmentId !== null}
        onOpenChange={(open) => !open && setDeleteAttachmentId(null)}
        description={t('attachments.confirmDelete', 'Biztosan törlöd ezt a mellékletet?')}
        onConfirm={() => {
          if (deleteAttachmentId) {
            deleteMutation.mutate(deleteAttachmentId)
            setDeleteAttachmentId(null)
          }
        }}
      />

      {/* Attachment dropzone */}
      {canManageAttachments && (
        <Card>
          <CardHeader>
            <CardTitle>{t('attachments.upload')}</CardTitle>
            <CardDescription>
              {t('attachments.maxSize')} • {t('attachments.maxPerDevice')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div
              {...getRootProps()}
              className={`flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 transition-colors ${
                isDragActive ? 'border-primary bg-primary/5' : 'border-muted-foreground/25'
              }`}
            >
              <input {...getInputProps()} />
              <Upload className="mb-2 h-8 w-8 text-muted-foreground" />
              <p className="text-sm text-muted-foreground">{t('attachments.dragDrop')}</p>
            </div>

            {uploadMutation.isPending && (
              <div className="mt-3 space-y-2">
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span className="truncate">
                    {t('attachments.uploading')}: {uploadingFileName}
                  </span>
                  <span className="font-mono">{uploadProgress}%</span>
                </div>
                <div
                  role="progressbar"
                  aria-valuenow={uploadProgress}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-label={t('attachments.uploading')}
                  className="h-2 w-full overflow-hidden rounded-full bg-muted"
                >
                  <div
                    className="h-full bg-primary transition-all duration-200"
                    style={{ width: `${uploadProgress}%` }}
                  />
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Attachment lista */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Paperclip className="h-5 w-5" />
            {t('attachments.title')} ({attachments?.length ?? 0}/5)
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-muted-foreground">{t('common.loading')}...</p>
          ) : !attachments || attachments.length === 0 ? (
            <p className="text-muted-foreground">{t('common.noData')}</p>
          ) : (
            <ul className="space-y-2">
              {attachments.map((att) => (
                <AttachmentItem
                  key={att.id}
                  attachment={att}
                  canDelete={canManageAttachments}
                  onPreview={() => setPreviewingAttachment(att)}
                  onDownload={() => window.open(downloadUrl(att.id), '_blank')}
                  onDelete={() => setDeleteAttachmentId(att.id)}
                />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteDeviceDialogOpen}
        onOpenChange={setDeleteDeviceDialogOpen}
        description={t(
          'devices.confirmDelete',
          'Biztosan véglegesen törölni szeretnéd ezt az eszközt? A művelet nem vonható vissza.'
        )}
        loading={deleteDeviceMutation.isPending}
        onConfirm={() => deleteDeviceMutation.mutate()}
      />
    </div>
  )
}

function AttachmentItem({
  attachment,
  canDelete,
  onPreview,
  onDownload,
  onDelete,
}: {
  attachment: DeviceAttachment
  canDelete?: boolean
  onPreview: () => void
  onDownload: () => void
  onDelete: () => void
}) {
  const { t } = useTranslation()
  const previewable = canPreview(attachment.mimeType)

  return (
    <li className="flex items-center justify-between rounded-md border border-border bg-card p-3">
      <div className="flex items-center gap-3">
        <FileText className="h-5 w-5 text-muted-foreground" />
        <div>
          <p className="text-sm font-medium">{attachment.fileName}</p>
          <p className="text-xs text-muted-foreground">
            <Badge variant="outline" className="mr-2 text-xs">
              {attachment.mimeType}
            </Badge>
            {formatFileSize(attachment.sizeBytes)}
            {' • '}
            {t('attachments.uploadedAt')}: {new Date(attachment.uploadedAt).toLocaleString()}
          </p>
        </div>
      </div>
      <div className="flex items-center gap-1">
        {previewable && (
          <Button variant="ghost" size="icon" title={t('attachments.preview')} onClick={onPreview}>
            <Eye className="h-4 w-4 text-primary" />
          </Button>
        )}
        <Button variant="ghost" size="icon" title={t('attachments.download')} onClick={onDownload}>
          <Download className="h-4 w-4" />
        </Button>
        {canDelete && (
          <Button variant="ghost" size="icon" title={t('common.delete')} onClick={onDelete}>
            <Trash2 className="h-4 w-4 text-destructive" />
          </Button>
        )}
      </div>
    </li>
  )
}

function AttachmentPreviewDialog({
  attachment,
  onOpenChange,
}: {
  attachment: DeviceAttachment | null
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()

  if (!attachment) {
    return null
  }

  const isImage = attachment.mimeType.startsWith('image/')
  const isPdf = attachment.mimeType === 'application/pdf'
  const isText = attachment.mimeType.startsWith('text/')

  return (
    <Dialog open={!!attachment} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-4xl">
        <DialogHeader>
          <DialogTitle>{attachment.fileName}</DialogTitle>
          <DialogDescription>
            <Badge variant="outline" className="mr-2 text-xs">
              {attachment.mimeType}
            </Badge>
            {formatFileSize(attachment.sizeBytes)}
          </DialogDescription>
        </DialogHeader>
        <div className="max-h-[70vh] overflow-auto">
          {isImage && (
            <img
              src={previewUrl(attachment.id)}
              alt={attachment.fileName}
              className="mx-auto max-h-[65vh] max-w-full object-contain"
            />
          )}
          {isPdf && (
            <iframe
              src={previewUrl(attachment.id)}
              title={attachment.fileName}
              className="h-[65vh] w-full"
            />
          )}
          {isText && (
            <iframe
              src={previewUrl(attachment.id)}
              title={attachment.fileName}
              className="h-[65vh] w-full font-mono text-xs"
            />
          )}
          {!isImage && !isPdf && !isText && (
            <p className="text-muted-foreground">{t('attachments.previewNotSupported')}</p>
          )}
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => window.open(downloadUrl(attachment.id), '_blank')}
          >
            <Download className="mr-2 h-4 w-4" />
            {t('attachments.download')}
          </Button>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.close')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function AttachSoftwareDialog({
  open,
  onOpenChange,
  deviceId,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  deviceId: number
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [selectedSoftwareId, setSelectedSoftwareId] = useState<string>('')

  const { data: allSoftwarePage } = useQuery({
    queryKey: ['software', 'all'],
    queryFn: () => softwareApi.findAll({ page: 0, size: 50 }),
    enabled: open,
  })

  const attachMutation = useMutation({
    mutationFn: (softwareId: number) => deviceApi.attachSoftware(deviceId, softwareId),
    onSuccess: (_data, softwareId) => {
      queryClient.invalidateQueries({ queryKey: ['device-software', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['software-devices', softwareId] })
      toast.success(t('devices.softwareAttached'))
      setSelectedSoftwareId('')
      onOpenChange(false)
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('devices.addSoftware')}</DialogTitle>
          <DialogDescription>
            {t('devices.softwares')} — #{deviceId}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <Label htmlFor="software-select">{t('devices.selectSoftware')}</Label>
            <Select value={selectedSoftwareId} onValueChange={setSelectedSoftwareId}>
              <SelectTrigger id="software-select">
                <SelectValue placeholder={t('devices.selectSoftware')} />
              </SelectTrigger>
              <SelectContent>
                {!allSoftwarePage?.content || allSoftwarePage.content.length === 0 ? (
                  <SelectItem value="__none__" disabled>
                    {t('common.noData')}
                  </SelectItem>
                ) : (
                  allSoftwarePage.content.map((sw) => (
                    <SelectItem key={sw.id} value={String(sw.id)}>
                      {sw.name}
                    </SelectItem>
                  ))
                )}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={attachMutation.isPending}
          >
            {t('common.cancel')}
          </Button>
          <Button
            onClick={() => attachMutation.mutate(Number(selectedSoftwareId))}
            disabled={!selectedSoftwareId || attachMutation.isPending}
          >
            {t('devices.addSoftware')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
