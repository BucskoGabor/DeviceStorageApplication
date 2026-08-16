package hu.tanszek.device.import_.dto;

/**
 * Excel import execution result — a tényleges import futás eredménye.
 *
 * @param usersInserted újonnan létrehozott user-ek száma
 * @param usersUpdated frissített user-ek száma (UPDATE-or-SKIP)
 * @param devicesInserted újonnan létrehozott device-ok száma
 * @param devicesUpdated frissített device-ok száma
 * @param errors a futás során felmerült hibák (pl. DB constraint violation)
 */
public record ImportResult(
    int usersInserted, int usersUpdated, int devicesInserted, int devicesUpdated, int errors) {}
