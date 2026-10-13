package pe.registroacademico.nativo.domain.estudiante

/**
 * Token parseado de asistencia de ingreso al colegio ("LI.<sesion hex32>.<ventana>.<firma 12 hex>").
 * Mismo formato que supabase y contrato de QR rotativo de ingreso compartido.
 */
data class QrIngresoToken(
    val sesionHex: String,
    val ventana: Long,
    val firma: String
)

/**
 * Tipo de flujo de escaneo determinado por el prefijo del QR.
 */
enum class FlujoQr {
    INGRESO,
    CURSO,
    DESCONOCIDO
}

/**
 * Selecciona el flujo de QR (ingreso, curso o desconocido) según el prefijo del texto escaneado.
 * Función pura extraída para facilitar pruebas y desacoplar de la UI.
 */
fun seleccionarFlujoQr(texto: String?): FlujoQr {
    val limpio = texto?.trim() ?: return FlujoQr.DESCONOCIDO
    return when {
        limpio.startsWith("LI.") -> FlujoQr.INGRESO
        limpio.startsWith("LQ.") -> FlujoQr.CURSO
        else -> FlujoQr.DESCONOCIDO
    }
}

/**
 * Parsea el texto escaneado de un QR de ingreso al colegio.
 * Formato esperado: LI.<sesion32hex>.<ventana>.<firma12hex>.
 * Devuelve null si no cumple exactamente la estructura o sus validaciones.
 * Es tolerante a espacios en blanco al inicio y al final.
 */
fun parsearQrIngreso(texto: String?): QrIngresoToken? {
    if (texto.isNullOrBlank()) return null
    val partes = texto.trim().split(".")
    if (partes.size != 4) return null
    if (partes[0].trim() != "LI") return null

    val sesion = partes[1].trim()
    if (!Regex("^[0-9a-fA-F]{32}$").matches(sesion)) return null

    val ventanaStr = partes[2].trim()
    if (!Regex("^[0-9]{1,12}$").matches(ventanaStr)) return null
    val ventana = ventanaStr.toLongOrNull() ?: return null

    val firma = partes[3].trim()
    if (!Regex("^[0-9a-fA-F]{12}$").matches(firma)) return null

    return QrIngresoToken(
        sesionHex = sesion.lowercase(),
        ventana = ventana,
        firma = firma.lowercase()
    )
}
