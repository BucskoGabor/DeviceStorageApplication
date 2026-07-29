package hu.tanszek.device.attachment.entity;

import hu.tanszek.device.common.BaseEntity;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Eszköz attachment (fájl) entitás.
 *
 * <p>Fájlok fizikai tárolása: {@code ./uploads/devices/{device_id}/{uuid}.{ext}}
 * lokális volume mount-on ({@code uploads_data:/var/uploads}).
 *
 * <p>Limits:
 * <ul>
 *   <li>Max 5MB/fájl (validálás upload endpointon)</li>
 *   <li>Max 5 fájl/device (service check)</li>
 * </ul>
 *
 * <p>Cascade policy: device törlésekor az attachment rekordok ÉS a fizikai
 * fájlok is törlődnek (lásd {@code AttachmentService.deleteByDevice}).
 *
 * @see hu.tanszek.device.attachment.repository.DeviceAttachmentRepository
 */
@Entity
@Table(name = "device_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceAttachment extends BaseEntity<Long> {

    /** Az eszköz, amihez a fájl tartozik (cascade delete) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** A fájl eredeti neve (max 255 karakter) */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** MIME típus (pl. image/jpeg, application/pdf) */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** Fájl mérete bájtban */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Feltöltés időbélyege */
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** A feltöltést végző user */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private AppUser uploadedBy;

    /** Tárolási útvonal (formátum: ./uploads/devices/{device_id}/{uuid}.{ext}) */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;
}