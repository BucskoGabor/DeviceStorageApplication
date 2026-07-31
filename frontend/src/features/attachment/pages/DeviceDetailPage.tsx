import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useDropzone } from 'react-dropzone'
import { useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Upload, Trash2, FileText, Paperclip, ArrowRightCircle, ArrowLeftCircle, Plus, Key, Eye, Download } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { attachmentApi, formatFileSize, downloadUrl, previewUrl, canPreview, type DeviceAttachment } from '@/features/attachment/api/attachmentApi'
import { deviceApi, type Device } from '@/features/device/api/deviceApi'
import { softwareApi } from '@/features/software/api/softwareApi'
import { assignmentApi } from '@/features/assignment/api/assignmentApi'
import { useAuthStore } from '@/lib/store/authStore'
import { AssignmentDialog } from '@/features/assignment/components/AssignmentDialog'
import { AssignmentHistoryTable } from '@/features/assignment/components/AssignmentHistoryTable'
import { StatusBadge } from '@/features/assignment/components/StatusBadge'

/**
 * DeviceDetailPage — admin/devices/{id} oldal.
 *
 * Három fő szekció:
 * 1. Aktuális hozzárendelés + műveleti gombok (assign kérés / unassign kérés)
 * 2. Attachment-ek drag-drop feltöltése és listázása
 * 3. Assignment history táblázat
 */
export function DeviceDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const deviceId = Number(params.id)
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const [assignDialogOpen, setAssignDialogOpen] = useState(false)

  const canAssign = permissions.includes('DEVICE_ASSIGN')
  const canUnassign = permissions.includes('DEVICE_UNASSIGN')
  const canUpdateDevice = permissions.includes('DEVICE_UPDATE')

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

  const currentAssignment = assignmentsData?.content.find((a) => a.active)

  const unassignMutation = useMutation({
    mutationFn: (assignmentId: number) => assignmentApi.requestUnassignment(assignmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assignments', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['pending-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      toast.success(t('assignments.requestUnassignmentSuccess'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const changeStatusMutation = useMutation({
    mutationFn: (newStatus: Device['status']) => deviceApi.changeStatus(deviceId, newStatus),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      toast.success(t('devices.statusChanged'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
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
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
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
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
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
    if (files.length === 0 || !files[0]) return
    uploadMutation.mutate(files[0])
  }

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'image/*': ['.png', '.jpg', '.jpeg', '.gif', '.webp'],
      'application/pdf': ['.pdf'],
      'application/msword': ['.doc', '.docx'],
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'],
      'text/plain': ['.txt'],
    },
    maxSize: 5 * 1024 * 1024,
    maxFiles: 1,
  })

  return (
    <div className="space-y-6">
      <h1 className="mb-2 text-2xl font-semibold">
        {t('devices.title')} #{deviceId}
      </h1>
      {device && (
        <div className="mb-6 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
          <span>{device.inventoryNumber}</span>
          <span>•</span>
          <span>{device.type}</span>
          <span>•</span>
          {canUpdateDevice ? (
            <Select
              value={device.status}
              disabled={changeStatusMutation.isPending}
              onValueChange={(value: Device['status']) => {
                if (value !== device.status) {
                  changeStatusMutation.mutate(value)
                }
              }}
            >
              <SelectTrigger className="h-8 w-[160px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PENDING">{t('devices.statusPending')}</SelectItem>
                <SelectItem value="IN_STORAGE">{t('devices.statusInStorage')}</SelectItem>
                <SelectItem value="ASSIGNED">{t('devices.statusAssigned')}</SelectItem>
                <SelectItem value="MAINTENANCE">{t('devices.statusMaintenance')}</SelectItem>
                <SelectItem value="DISPOSED">{t('devices.statusDisposed')}</SelectItem>
              </SelectContent>
            </Select>
          ) : (
            <span>{t(`devices.status${device.status.charAt(0) + device.status.slice(1).toLowerCase()}`)}</span>
          )}
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
                {canAssign && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setAssignDialogOpen(true)}
                  >
                    <ArrowRightCircle className="mr-1 h-4 w-4" />
                    {t('devices.assign')}
                  </Button>
                )}
                {canUnassign && (
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={unassignMutation.isPending}
                    onClick={() => {
                      if (window.confirm(t('devices.confirmUnassign'))) {
                        unassignMutation.mutate(currentAssignment.id)
                      }
                    }}
                  >
                    <ArrowLeftCircle className="mr-1 h-4 w-4" />
                    {t('devices.unassign')}
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                {t('assignments.noActiveAssignment')}
              </p>
              {canAssign && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setAssignDialogOpen(true)}
                >
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
            <Button
              size="sm"
              variant="outline"
              onClick={() => setSoftwareDialogOpen(true)}
            >
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
                        {sw.licenseKey ?? sw.licenseKeyMasked ?? '—'}
                      </p>
                    </div>
                  </div>
                  {canUpdateDevice && (
                    <Button
                      variant="ghost"
                      size="icon"
                      disabled={detachMutation.isPending}
                      onClick={() => {
                        if (window.confirm(t('devices.confirmDetachSoftware'))) {
                          detachMutation.mutate(sw.id)
                        }
                      }}
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

      {/* Attachment dropzone */}
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
                  onPreview={() => setPreviewingAttachment(att)}
                  onDownload={() => window.open(downloadUrl(att.id), '_blank')}
                  onDelete={() => {
                    if (window.confirm(t('attachments.confirmDelete'))) {
                      deleteMutation.mutate(att.id)
                    }
                  }}
                />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function AttachmentItem({
  attachment,
  onPreview,
  onDownload,
  onDelete,
}: {
  attachment: DeviceAttachment
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
          <Button
            variant="ghost"
            size="icon"
            title={t('attachments.preview')}
            onClick={onPreview}
          >
            <Eye className="h-4 w-4 text-primary" />
          </Button>
        )}
        <Button
          variant="ghost"
          size="icon"
          title={t('attachments.download')}
          onClick={onDownload}
        >
          <Download className="h-4 w-4" />
        </Button>
        <Button variant="ghost" size="icon" title={t('common.delete')} onClick={onDelete}>
          <Trash2 className="h-4 w-4 text-destructive" />
        </Button>
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
          <Button variant="outline" onClick={() => window.open(downloadUrl(attachment.id), '_blank')}>
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
                {(!allSoftwarePage?.content || allSoftwarePage.content.length === 0) ? (
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
