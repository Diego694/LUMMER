package pe.registroacademico.nativo.domain.docente

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import pe.registroacademico.nativo.data.model.Curso
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.EnumMap
import java.util.Locale

object DocenteQrUtils {

    const val VENTANA_SEG = 30L
    private val ZONE_LIMA: ZoneId = ZoneId.of("America/Lima")
    private val FORMATO_HORA_SEG = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)

    /**
     * Calcula los segundos restantes en la ventana de 30 segundos actual.
     * Si faltan <= 0 retorna 30.
     */
    fun calcularSegundosRestantes(epochMs: Long): Long {
        val segTotal = epochMs / 1000L
        val rem = segTotal % VENTANA_SEG
        val restantes = VENTANA_SEG - rem
        return if (restantes <= 0L) VENTANA_SEG else restantes
    }

    /**
     * Calcula el índice de ventana de 30 segundos (epochMs / 30000).
     */
    fun calcularVentana30s(epochMs: Long): Long {
        return Math.floorDiv(epochMs, 30_000L)
    }

    /**
     * Extrae la hora exacta con segundos (HH:mm:ss) en la zona horaria America/Lima.
     */
    fun formatearHoraExacta(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val instant = Instant.parse(iso)
            instant.atZone(ZONE_LIMA).format(FORMATO_HORA_SEG)
        } catch (_: Throwable) {
            // Si el texto ya viene como "HH:mm:ss"
            if (iso.length in 5..8 && iso.contains(":")) {
                if (iso.length == 5) "$iso:00" else iso
            } else {
                iso.takeLast(8)
            }
        }
    }

    /**
     * Extrae la fecha (dd/MM/yyyy) en la zona horaria America/Lima.
     */
    fun formatearFechaExacta(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val instant = Instant.parse(iso)
            instant.atZone(ZONE_LIMA).format(FORMATO_FECHA)
        } catch (_: Throwable) {
            if (iso.length >= 10 && iso.contains("-")) {
                val partes = iso.take(10).split("-")
                if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else iso.take(10)
            } else {
                iso
            }
        }
    }

    /**
     * Retorna una etiqueta amigable y legible para el medio/origen del registro.
     */
    fun etiquetaOrigenLegible(origen: String?): String {
        return when (origen?.trim()?.lowercase(Locale.ROOT)) {
            "qr" -> "Carnet QR"
            "nfc" -> "NFC"
            "manual" -> "Manual"
            "alumno" -> "El propio estudiante"
            "masivo" -> "Masivo"
            "qr-docente" -> "QR del docente"
            "kiosco", "quiosco" -> "Quiosco"
            null, "" -> "—"
            else -> origen
        }
    }

    /**
     * Agrupa y ordena los cursos por carrera (nivel) y dentro de cada carrera por ciclo (grado) y nombre.
     */
    fun agruparCursosPorCarrera(cursos: List<Curso>): Map<String, List<Curso>> {
        val agrupados = cursos.groupBy { it.nivel.ifBlank { "General" } }
        val resultado = sortedMapOf<String, List<Curso>>(compareBy { it })

        for ((carrera, lista) in agrupados) {
            val ordenada = lista.sortedWith(
                compareBy<Curso> { it.grado.orEmpty() }
                    .thenBy { it.nombre }
            )
            resultado[carrera] = ordenada
        }
        return resultado
    }

    /**
     * Genera un mapa de bits (Bitmap) del código QR utilizando ZXing QRCodeWriter.
     */
    fun generarQrBitmap(texto: String, sizePx: Int = 512): Bitmap {
        if (texto.isBlank()) {
            val bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            bm.eraseColor(Color.WHITE)
            return bm
        }

        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 2)
        }

        val bitMatrix = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    const val INTERVALO_DEFECTO_SEG = 30
    val INTERVALOS_VALIDOS_INGRESO = listOf(15, 30)

    /**
     * Valida si un intervalo en segundos para el QR rotativo de ingreso está dentro
     * del rango permitido por la base de datos (10 a 60 segundos).
     */
    fun esIntervaloValido(segundos: Int): Boolean {
        return segundos in 10..60
    }

    /**
     * Normaliza el intervalo seleccionado para el quiosco (15 s o 30 s).
     * Si no coincide con ninguna opción admitida, retorna el valor por defecto (30 s).
     */
    fun normalizarIntervaloIngreso(segundos: Int?): Int {
        return if (segundos == 15 || segundos == 30) segundos else INTERVALO_DEFECTO_SEG
    }

    /**
     * Calcula los segundos restantes en una ventana con intervalo dinámico (15 o 30 s).
     * Si faltan <= 0 retorna el intervalo completo.
     */
    fun calcularSegundosRestantesIntervalo(epochMs: Long, intervaloSeg: Int): Long {
        val intervalo = if (intervaloSeg <= 0) INTERVALO_DEFECTO_SEG.toLong() else intervaloSeg.toLong()
        val segTotal = epochMs / 1000L
        val rem = segTotal % intervalo
        val restantes = intervalo - rem
        return if (restantes <= 0L) intervalo else restantes
    }

    /**
     * Calcula los milisegundos restantes hasta el siguiente refresco programado,
     * considerando un margen de anticipación en ms (por defecto 1000 ms = 1 s antes de expirar).
     * Si el tiempo calculado es <= 0 ms, retorna 0 ms indicando refresco inmediato.
     */
    fun calcularMsHastaSiguienteRefresco(epochMs: Long, intervaloSeg: Int, margenMs: Long = 1000L): Long {
        val segsRestantes = calcularSegundosRestantesIntervalo(epochMs, intervaloSeg)
        val msRestantesEnSegundo = 1000L - (epochMs % 1000L)
        val msTotalesRestantes = ((segsRestantes - 1L) * 1000L) + msRestantesEnSegundo
        val esperaMs = msTotalesRestantes - margenMs
        return if (esperaMs <= 0L) 0L else esperaMs
    }

    /**
     * Calcula el índice de ventana según epochMs y el intervalo en segundos (floor(epoch_segundos / intervalo)).
     */
    fun calcularVentanaIngreso(epochMs: Long, intervaloSeg: Int): Long {
        val intervalo = if (intervaloSeg <= 0) INTERVALO_DEFECTO_SEG.toLong() else intervaloSeg.toLong()
        val segTotal = epochMs / 1000L
        return Math.floorDiv(segTotal, intervalo)
    }

    /**
     * Verifica si una cadena corresponde al formato de token rotativo de ingreso (prefijo 'LI.').
     */
    fun esTokenQrIngreso(texto: String?): Boolean {
        if (texto.isNullOrBlank()) return false
        return texto.startsWith("LI.")
    }
}

