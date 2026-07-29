package hu.tanszek.device.import_.dto;

import java.util.List;

/**
 * Excel import preview response — a feltöltött fájl száraz elemzése.
 *
 * <p>A frontend megjeleníti a valid/invalid sorokat, és a user megerősíti
 * az importot.
 *
 * @param totalRows a fájl összes sorainak száma (a header után)
 * @param validRows az érvényes sorok listája (validálandó adatokkal)
 * @param invalidRows a hibás sorok listája (sor-szám + hibaüzenet)
 */
public record ImportPreviewResponse(
        int totalRows,
        List<ImportUserRow> validUsers,
        List<ImportDeviceRow> validDevices,
        List<InvalidRow> invalidRows
) {
    /**
     * Egy hibás sor reprezentációja.
     */
    public record InvalidRow(
            int rowNumber,  // Excel sor száma (1-től, a header a 0. sor)
            String entityType,  // "User" vagy "Device"
            String rawData,  // A nyers sor adatai (debug célokra)
            List<String> errors  // Validációs hibák listája
    ) {}
}