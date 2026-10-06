package pe.registroacademico.nativo.registro

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.SalidaFila

class OfflineColaLogicTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testSerializacionYDeserializacionAsistenciaOffline() {
        val original = Asistencia(
            colegioId = "col-123",
            alumnoId = "alu-456",
            fecha = "2026-10-05",
            hora = "08:10",
            registradoPor = "usr-789",
            origen = "qr"
        )

        val claveEsperada = "a|${original.alumnoId}|${original.fecha}"
        assertEquals("a|alu-456|2026-10-05", claveEsperada)

        val payload = json.encodeToString(original)
        val deserializado = json.decodeFromString<Asistencia>(payload)

        assertEquals(original.colegioId, deserializado.colegioId)
        assertEquals(original.alumnoId, deserializado.alumnoId)
        assertEquals(original.fecha, deserializado.fecha)
        assertEquals(original.hora, deserializado.hora)
        assertEquals(original.origen, deserializado.origen)
    }

    @Test
    fun testSerializacionYDeserializacionSalidaOffline() {
        val original = SalidaFila(
            alumnoId = "alu-999",
            fecha = "2026-10-05",
            hora = "13:30"
        )

        val claveEsperada = "s|${original.alumnoId}|${original.fecha}"
        assertEquals("s|alu-999|2026-10-05", claveEsperada)

        val payload = json.encodeToString(original)
        val deserializado = json.decodeFromString<SalidaFila>(payload)

        assertEquals(original.alumnoId, deserializado.alumnoId)
        assertEquals(original.fecha, deserializado.fecha)
        assertEquals(original.hora, deserializado.hora)
    }
}
