package pe.registroacademico.nativo.ui.gestion.qr

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object QrSeguro {
    const val VENTANA_MS: Long = 30000L

    fun ventana(ms: Long): Long = ms / VENTANA_MS

    fun segundosRestantes(ms: Long): Int {
        val rem = VENTANA_MS - (ms % VENTANA_MS)
        return ((rem + 999) / 1000).toInt()
    }

    fun qrModoEfectivo(modo: String?): String {
        val m = modo?.trim()?.lowercase() ?: ""
        return if (m in listOf("off", "opcional", "obligatorio")) m else "off"
    }

    fun calcularFirma(secreto: String, mensaje: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val key = SecretKeySpec(secreto.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            mac.init(key)
            val bytes = mac.doFinal(mensaje.toByteArray(StandardCharsets.UTF_8))
            bytes.take(5).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "0000000000"
        }
    }

    fun generarCodigo(codigo: String, secreto: String?, ms: Long, qrModo: String = "off"): String {
        val modo = qrModoEfectivo(qrModo)
        if (modo == "off" || secreto.isNullOrBlank()) {
            return codigo
        }
        val w = ventana(ms)
        val sig = calcularFirma(secreto, "$codigo.$w")
        val base36 = java.lang.Long.toString(w, 36)
        return "$codigo.$base36.$sig"
    }
}

/**
 * Generador autónomo de matriz QR (Versión 1 a 4, Byte Mode, Medium EC) en Kotlin puro.
 * Permite dibujar el código QR directamente en un Canvas de Jetpack Compose sin librerías externas.
 */
object QrCodeGenerator {

    class QrMatrix(val size: Int, private val modules: Array<BooleanArray>) {
        fun isDark(row: Int, col: Int): Boolean = modules[row][col]
    }

    fun encode(text: String): QrMatrix {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val version = when {
            bytes.size <= 14 -> 1 // V1: 21x21, M: 14 bytes
            bytes.size <= 26 -> 2 // V2: 25x25, M: 26 bytes
            bytes.size <= 42 -> 3 // V3: 29x29, M: 42 bytes
            else -> 4             // V4: 33x33, M: 62 bytes
        }
        return generateQr(bytes, version)
    }

    private val TOTAL_CODEWORDS = intArrayOf(0, 26, 44, 70, 100)
    private val DATA_CODEWORDS_M = intArrayOf(0, 16, 28, 44, 64)
    private val EC_CODEWORDS_M = intArrayOf(0, 10, 16, 26, 36)

    private fun generateQr(dataBytes: ByteArray, version: Int): QrMatrix {
        val size = 17 + 4 * version
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun setFunc(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                modules[r][c] = dark
                isFunction[r][c] = true
            }
        }

        // 1. Finder patterns
        fun drawFinder(topRow: Int, leftCol: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    setFunc(topRow + r, leftCol + c, isBorder || isCenter)
                }
            }
            // Separator around finder
            for (r in -1..7) {
                for (c in -1..7) {
                    if (r == -1 || r == 7 || c == -1 || c == 7) {
                        setFunc(topRow + r, leftCol + c, false)
                    }
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // 2. Alignment patterns (version >= 2)
        if (version >= 2) {
            val alignPos = when (version) {
                2 -> intArrayOf(6, 18)
                3 -> intArrayOf(6, 22)
                4 -> intArrayOf(6, 26)
                else -> intArrayOf(6, 18)
            }
            for (r in alignPos) {
                for (c in alignPos) {
                    val inFinder = (r <= 8 && c <= 8) || (r <= 8 && c >= size - 8) || (r >= size - 8 && c <= 8)
                    if (!inFinder) {
                        for (dr in -2..2) {
                            for (dc in -2..2) {
                                val border = dr == -2 || dr == 2 || dc == -2 || dc == 2
                                val center = dr == 0 && dc == 0
                                setFunc(r + dr, c + dc, border || center)
                            }
                        }
                    }
                }
            }
        }

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            setFunc(6, i, dark)
            setFunc(i, 6, dark)
        }

        // Dark module
        setFunc(4 * version + 9, 8, true)

        // 4. Reserve format info areas
        for (i in 0..8) {
            setFunc(8, i, false)
            setFunc(i, 8, false)
            setFunc(8, size - 1 - i, false)
            setFunc(size - 1 - i, 8, false)
        }

        // 5. Data encoding (Byte mode: indicator 0100, length, data, terminator, padding)
        val dataCap = DATA_CODEWORDS_M[version]
        val bitBuffer = ArrayList<Int>()

        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bitBuffer.add((value shr i) and 1)
            }
        }

        // Mode: 0100 (8-bit Byte)
        appendBits(4, 4)
        // Character count indicator (8 bits for V1-V9 in byte mode)
        appendBits(dataBytes.size, 8)
        for (b in dataBytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        // Terminator (up to 4 zeros)
        val capBits = dataCap * 8
        val termLen = minOf(4, capBits - bitBuffer.size)
        appendBits(0, termLen)
        // Pad to byte boundary
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(0)
        }
        // Pad bytes 0xEC, 0x11
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.size < capBits) {
            appendBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        val dataCodewords = ByteArray(dataCap)
        for (i in 0 until dataCap) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bitBuffer[i * 8 + b]
            }
            dataCodewords[i] = v.toByte()
        }

        // 6. Reed-Solomon Error Correction
        val ecCount = EC_CODEWORDS_M[version]
        val ecCodewords = calculateErrorCorrection(dataCodewords, ecCount)

        // Combine all codewords
        val totalCodewords = ByteArray(TOTAL_CODEWORDS[version])
        System.arraycopy(dataCodewords, 0, totalCodewords, 0, dataCap)
        System.arraycopy(ecCodewords, 0, totalCodewords, dataCap, ecCount)

        // All bits to place
        val allBits = BooleanArray(totalCodewords.size * 8)
        for (i in totalCodewords.indices) {
            val v = totalCodewords[i].toInt() and 0xFF
            for (b in 0..7) {
                allBits[i * 8 + b] = ((v shr (7 - b)) and 1) == 1
            }
        }

        // 7. Place data bits in matrix (columns 2 at a time from right to left)
        var bitIdx = 0
        var right = size - 1
        var goingUp = true

        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing column
            val rows = if (goingUp) (size - 1 downTo 0).toList() else (0 until size).toList()
            for (r in rows) {
                for (c in intArrayOf(right, right - 1)) {
                    if (!isFunction[r][c]) {
                        val bit = if (bitIdx < allBits.size) allBits[bitIdx++] else false
                        // Apply Mask Pattern 0: (row + column) % 2 == 0
                        val mask = (r + c) % 2 == 0
                        modules[r][c] = if (mask) !bit else bit
                    }
                }
            }
            right -= 2
            goingUp = !goingUp
        }

        // 8. Format Information (Mask 0, EC level M = 00)
        // EC Level M = 00, Mask 0 = 000 -> 5 bits: 00000
        // BCH code + XOR mask 101010000010010:
        // For EC M and Mask 0: raw 00000 -> formatted = 101010000010010 (0x5412)
        val formatBits = 0x5412
        for (i in 0..14) {
            val bit = ((formatBits shr (14 - i)) and 1) == 1
            // Placement around top-left
            when {
                i < 6 -> modules[8][i] = bit
                i == 6 -> modules[8][7] = bit
                i == 7 -> modules[8][8] = bit
                i == 8 -> modules[7][8] = bit
                else -> modules[14 - i][8] = bit
            }
            // Placement around top-right and bottom-left
            when {
                i < 8 -> modules[size - 1 - i][8] = bit
                else -> modules[8][size - 15 + i] = bit
            }
        }

        return QrMatrix(size, modules)
    }

    // GF(256) log and antilog tables (primitive polynomial 0x11D)
    private val expTable = IntArray(512)
    private val logTable = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            expTable[i] = x
            expTable[i + 255] = x
            logTable[x] = i
            x = (x shl 1)
            if (x >= 256) x = x xor 0x11D
        }
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return expTable[logTable[x] + logTable[y]]
    }

    private fun calculateErrorCorrection(data: ByteArray, ecCount: Int): ByteArray {
        // Generator polynomial g(x) = (x - a^0)(x - a^1)...(x - a^(ecCount-1))
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val root = expTable[i]
            val nextGen = IntArray(gen.size + 1)
            for (j in gen.indices) {
                nextGen[j] = nextGen[j] xor gen[j]
                nextGen[j + 1] = nextGen[j + 1] xor gfMul(gen[j], root)
            }
            gen = nextGen
        }

        val result = IntArray(ecCount)
        for (b in data) {
            val lead = (b.toInt() and 0xFF) xor result[0]
            for (j in 0 until ecCount - 1) {
                result[j] = result[j + 1] xor gfMul(gen[j + 1], lead)
            }
            result[ecCount - 1] = gfMul(gen[ecCount], lead)
        }

        val out = ByteArray(ecCount)
        for (i in 0 until ecCount) {
            out[i] = result[i].toByte()
        }
        return out
    }
}
