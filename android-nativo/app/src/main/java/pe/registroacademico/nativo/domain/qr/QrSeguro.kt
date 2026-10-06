package pe.registroacademico.nativo.domain.qr

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Puerto fiel a Kotlin de la lógica de assets/js/qr-seguro.js
 *
 * El QR dinámico cambia cada 30 segundos y lleva una firma HMAC-SHA256 calculada
 * con un secreto que solo conocen el estudiante y el instituto.
 * Formato del QR: <codigo>.<ventana_en_base_36>.<firma_10_hex>
 */

const val VENTANA_MS: Long = 30_000L
const val TOLERANCIA: Long = 1L // Tolerancia de ventanas aceptadas a cada lado

/**
 * Calcula la firma HMAC-SHA256 del mensaje con el secreto proporcionado.
 * Retorna los primeros 5 bytes en formato hexadecimal (10 caracteres hex en minúsculas).
 */
fun firma(secreto: String, mensaje: String): String {
    val hmac = Mac.getInstance("HmacSHA256")
    val secretKey = SecretKeySpec(secreto.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    hmac.init(secretKey)
    val hash = hmac.doFinal(mensaje.toByteArray(StandardCharsets.UTF_8))
    return hash.take(5).joinToString("") { "%02x".format(it) }
}

/**
 * Retorna el número de ventana para un timestamp en milisegundos.
 */
fun ventana(ms: Long): Long = Math.floorDiv(ms, VENTANA_MS)

/**
 * Retorna los segundos restantes dentro de la ventana actual.
 */
fun segundosRestantes(ms: Long): Long {
    val rem = ms % VENTANA_MS
    val dif = VENTANA_MS - if (rem < 0) rem + VENTANA_MS else rem
    return Math.ceil(dif / 1000.0).toLong()
}

/**
 * Genera el texto del código QR dinámico seguro.
 * Formato: <codigo>.<ventana36>.<firma10hex>
 */
fun generarQR(codigo: String, secreto: String, ms: Long): String {
    val w = ventana(ms)
    val t36 = java.lang.Long.toString(w, 36)
    val sig = firma(secreto, "$codigo.$w")
    return "$codigo.$t36.$sig"
}

/**
 * Motivos estándar de rechazo de un código QR.
 */
object MotivoRechazo {
    const val DESCONOCIDO = "desconocido"
    const val SIN_SECRETO = "sinsecreto"
    const val VENCIDO = "vencido"
    const val FIRMA = "firma"
}

/**
 * Representación del resultado de verificación de un QR.
 * - [Estatico]: El código no tiene firma dinámica o coincide directamente con un código base.
 * - [Ok]: Código dinámico válido y vigente.
 * - [Rechazo]: Código inválido por motivo específico (desconocido, sin secreto, vencido, firma incorrecta).
 */
sealed class QrResultado<out T> {
    data object Estatico : QrResultado<Nothing>()
    data class Ok<T>(val alumno: T) : QrResultado<T>()
    data class Rechazo<T>(val motivo: String, val alumno: T? = null) : QrResultado<T>()
}

/**
 * Modelo de datos básico para verificación de QR.
 */
data class AlumnoQr(
    val id: String = "",
    val codigo: String = "",
    val qrSecreto: String? = null
)

/**
 * Verifica el texto leído por el escáner.
 *
 * Devuelve:
 * - [QrResultado.Estatico] si el texto coincide con un código directo o no tiene 3 componentes.
 * - [QrResultado.Ok] si la firma es válida y está dentro de la ventana de tolerancia.
 * - [QrResultado.Rechazo] con el motivo correspondiente ('desconocido', 'sinsecreto', 'vencido', 'firma').
 */
fun <T> verificarQR(
    texto: String,
    buscarPorCodigo: (String) -> T?,
    ms: Long,
    obtenerSecreto: (T) -> String?
): QrResultado<T> {
    // 1. Un código existente directamente (aunque contenga puntos) es estático
    if (buscarPorCodigo(texto) != null) {
        return QrResultado.Estatico
    }

    // 2. Si no tiene exactamente 3 partes separadas por '.', se trata como estático
    val partes = texto.split(".")
    if (partes.size != 3) {
        return QrResultado.Estatico
    }

    val codigo = partes[0]
    val t36 = partes[1]
    val sig = partes[2]

    // 3. Buscar el alumno por su código base
    val alumno = buscarPorCodigo(codigo)
        ?: return QrResultado.Rechazo(motivo = MotivoRechazo.DESCONOCIDO)

    // 4. Si el alumno no tiene secreto asignado
    val secreto = obtenerSecreto(alumno)
    if (secreto.isNullOrBlank()) {
        return QrResultado.Rechazo(motivo = MotivoRechazo.SIN_SECRETO, alumno = alumno)
    }

    // 5. Parsear la ventana en base 36 y validar que esté dentro de la tolerancia
    val w = t36.toLongOrNull(radix = 36)
    if (w == null || abs(ventana(ms) - w) > TOLERANCIA) {
        return QrResultado.Rechazo(motivo = MotivoRechazo.VENCIDO, alumno = alumno)
    }

    // 6. Validar firma HMAC-SHA256
    val firmaCalculada = firma(secreto, "$codigo.$w")
    return if (firmaCalculada == sig) {
        QrResultado.Ok(alumno = alumno)
    } else {
        QrResultado.Rechazo(motivo = MotivoRechazo.FIRMA, alumno = alumno)
    }
}

/**
 * Sobrecarga conveniente de [verificarQR] para el modelo [AlumnoQr].
 */
fun verificarQR(
    texto: String,
    buscarPorCodigo: (String) -> AlumnoQr?,
    ms: Long = System.currentTimeMillis()
): QrResultado<AlumnoQr> {
    return verificarQR(
        texto = texto,
        buscarPorCodigo = buscarPorCodigo,
        ms = ms,
        obtenerSecreto = { it.qrSecreto }
    )
}
