package pe.registroacademico.nativo.ui.estudiante

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import pe.registroacademico.nativo.data.estudiante.AlumnoPropio
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.domain.qr.generarQR
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Funciones puras de formateo y generación de contenido para el módulo de estudiante.
 */
object EstFormato {

    /**
     * Formatea una fecha ISO o YYYY-MM-DD al estándar amigable de Perú (es-PE),
     * por ejemplo: "13 de octubre de 2026".
     *
     * La lógica es independiente de la zona horaria del sistema:
     * - Fechas sin hora (yyyy-MM-dd) se parsean en zona America/Lima para preservar el día calendario.
     * - Cadenas con hora ('T' o ' ') representan instantes de Supabase en UTC y se proyectan a America/Lima.
     */
    fun formatearFechaAviso(fechaStr: String?): String {
        if (fechaStr.isNullOrBlank()) return ""
        val texto = fechaStr.trim()
        return try {
            val patron = when {
                texto.contains("T") -> "yyyy-MM-dd'T'HH:mm"
                texto.contains(" ") -> "yyyy-MM-dd HH:mm"
                else -> "yyyy-MM-dd"
            }
            val sdfEntrada = SimpleDateFormat(patron, Locale.ROOT).apply {
                timeZone = if (patron == "yyyy-MM-dd") {
                    TimeZone.getTimeZone("America/Lima")
                } else {
                    TimeZone.getTimeZone("UTC")
                }
            }
            val fecha = sdfEntrada.parse(texto.take(patron.length))
            if (fecha != null) {
                val sdfSalida = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "PE")).apply {
                    timeZone = TimeZone.getTimeZone("America/Lima")
                }
                sdfSalida.format(fecha)
            } else {
                texto
            }
        } catch (_: Exception) {
            texto
        }
    }

    /**
     * Extrae las iniciales del nombre de un estudiante (hasta 2 letras en mayúsculas).
     */
    fun obtenerIniciales(nombre: String?): String = StringUtils.initials(nombre)

    /**
     * Determina el contenido del código QR del carnet estudiantil, imitando exactamente main.js:
     * Si qrModo != "off" y el alumno tiene qrSecreto, genera el QR dinámico seguro con HMAC.
     * En caso contrario, retorna el código estático del alumno.
     */
    fun contenidoQrCarnet(
        codigo: String,
        qrSecreto: String?,
        qrModo: String?,
        ms: Long = System.currentTimeMillis()
    ): String {
        val modo = (qrModo ?: "").trim().lowercase()
        val esDinamico = modo != "off" && !qrSecreto.isNullOrBlank()
        return if (esDinamico) {
            generarQR(codigo, qrSecreto!!, ms)
        } else {
            codigo
        }
    }

    /**
     * Sobrecarga conveniente para [AlumnoPropio].
     */
    fun contenidoQrCarnet(
        alumno: AlumnoPropio,
        qrModo: String?,
        ms: Long = System.currentTimeMillis()
    ): String = contenidoQrCarnet(alumno.codigo, alumno.qrSecreto, qrModo, ms)

    /**
     * Genera un [Bitmap] con el código QR renderizado con ZXing.
     * Devuelve null si ocurre un fallo al codificar.
     */
    fun generarQrBitmap(contenido: String, tamanoPx: Int = 512): Bitmap? {
        if (contenido.isBlank()) return null
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(
                contenido,
                BarcodeFormat.QR_CODE,
                tamanoPx,
                tamanoPx
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
