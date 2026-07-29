import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useDropzone } from 'react-dropzone'
import { useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Upload, Trash2, FileText, Paperclip } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { attachmentApi, formatFileSize, type DeviceAttachment } from '@/features/attachment/api/attachmentApi'
import { deviceApi, type Device } from '@/features/device/api/deviceApi'
import { AdminLayout } from '@/features/admin/layouts/AdminLayout'

/**
 * DeviceDetailPage — admin/devices/{id} oldal, ahol az attachment-ek
 * drag-drop feltöltése és listázása történik.
 */
export function DeviceDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const deviceId = Number(params.id)
  const queryClient = useQueryClient()

  const { data: device } = useQuery({
    queryKey: ['device', deviceId],
    queryFn: () => deviceApi.findDeviceById(deviceId),
  })

  const { data: attachments, isLoading } = useQuery({
    queryKey: ['attachments', deviceId],
    queryFn: () => attachmentApi.findByDevice(deviceId),
  })

  const uploadMutation = useMutation({
    mutationFn: (file: File) => attachmentApi.upload(deviceId, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attachments', deviceId] })
      toast.success(t('attachments.title') + ' ✓', { position: 'top-right' })
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey), { position: 'top-right' })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => attachmentApi.deleteAttachment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attachments', deviceId] })
      toast.success(t('common.success'), { position: 'top-right' })
    },
  })

  const onDrop = (files: File[]) => {
    if (files.length === 0) return
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
    <AdminLayout>
      <h1 className="mb-2 text-2xl font-semibold">
        {t('devices.title')} #{deviceId}
      </h1>
      {device && (
        <p className="mb-6 text-sm text-muted-foreground">
          {device.inventoryNumber} • {device.type} • {device.status}
        </p>
      )}

      {/* Dropzone */}
      <Card className="mb-6">
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
            {uploadMutation.isPending && (
              <p className="mt-2 text-sm text-muted-foreground">{t('common.loading')}...</p>
            )}
          </div>
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
                  onDelete={() => {
                    if (confirm(t('attachments.confirmDelete'))) {
                      deleteMutation.mutate(att.id)
                    }
                  }}
                />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </AdminLayout>
  )
}

function AttachmentItem({
  attachment,
  onDelete,
}: {
  attachment: DeviceAttachment
  onDelete: () => void
}) {
  const { t } = useTranslation()
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
      <Button variant="ghost" size="icon" onClick={onDelete}>
        <Trash2 className="h-4 w-4 text-destructive" />
      </Button>
    </li>
  )
}
