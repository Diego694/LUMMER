package pe.registroacademico.nativo.ui.sistema

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pe.registroacademico.nativo.data.model.Auditoria

class AuditoriaDecoderTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDecodificarIdNumericoYDetalleObjetoInsert() {
        val rawJson = """
            {
                "id": 42,
                "colegio_id": "c1a2b3c4-1234-5678-9abc-def012345678",
                "user_id": "u1a2b3c4-1234-5678-9abc-def012345678",
                "usuario": "admin@instituto.edu.pe",
                "accion": "INSERT",
                "tabla": "alumnos",
                "registro_id": "reg-001",
                "detalle": {
                    "codigo": "A100",
                    "nivel": "COMPUTACION",
                    "grado": "I"
                },
                "creado_en": "2026-10-02T19:05:00Z"
            }
        """.trimIndent()

        val item = json.decodeFromString<Auditoria>(rawJson)
        assertEquals("42", item.id)
        assertEquals("INSERT", item.accion)
        assertEquals("alumnos", item.tabla)
        assertNotNull(item.detalle)

        val resumen = formatearResumen(item.accion, item.detalle)
        assertEquals("A100 · COMPUTACION · I", resumen)

        val cuando = formatearCuando(item.creadoEn)
        assertEquals("02 oct, 14:05", cuando)
    }

    @Test
    fun testDecodificarIdStringYDetalleUpdateConDeA() {
        val rawJson = """
            {
                "id": "uuid-string-123",
                "colegio_id": "c1a2b3c4-1234-5678-9abc-def012345678",
                "usuario": "coordinador@instituto.edu.pe",
                "accion": "UPDATE",
                "tabla": "alumnos",
                "registro_id": "reg-002",
                "detalle": {
                    "nombre": {
                        "de": "Juan",
                        "a": "Pedro"
                    },
                    "apoderado": {
                        "de": null,
                        "a": "Maria"
                    }
                },
                "creado_en": "2026-10-02T19:05:00Z"
            }
        """.trimIndent()

        val item = json.decodeFromString<Auditoria>(rawJson)
        assertEquals("uuid-string-123", item.id)
        assertEquals("UPDATE", item.accion)

        val resumen = formatearResumen(item.accion, item.detalle)
        assertEquals("nombre: Juan → Pedro · apoderado: vacío → Maria", resumen)
    }

    @Test
    fun testDecodificarDeleteConDetalleNull() {
        val rawJson = """
            {
                "id": 105,
                "accion": "DELETE",
                "tabla": "cursos",
                "registro_id": "reg-003",
                "detalle": null,
                "creado_en": "2026-10-02T19:05:00Z"
            }
        """.trimIndent()

        val item = json.decodeFromString<Auditoria>(rawJson)
        assertEquals("105", item.id)
        assertEquals("DELETE", item.accion)
        assertNull(item.detalle)

        val resumen = formatearResumen(item.accion, item.detalle)
        assertEquals("—", resumen)
    }

    @Test
    fun testDecodificarDetallePurgado() {
        val rawJson = """
            {
                "id": 106,
                "accion": "UPDATE",
                "tabla": "alumnos",
                "registro_id": "reg-004",
                "detalle": {
                    "purgado": "datos personales eliminados"
                },
                "creado_en": "2026-10-02T19:05:00Z"
            }
        """.trimIndent()

        val item = json.decodeFromString<Auditoria>(rawJson)
        val resumen = formatearResumen(item.accion, item.detalle)
        assertEquals("Datos personales eliminados", resumen)
    }

    @Test
    fun testFormatoFechaZonaLima() {
        // 2026-10-02T19:05:00Z en UTC -> 14:05 en America/Lima (UTC-5)
        val cuando = formatearCuando("2026-10-02T19:05:00Z")
        assertEquals("02 oct, 14:05", cuando)
    }

    @Test
    fun testToleraCamposDesconocidosYValoresNulos() {
        val rawJson = """
            {
                "id": 999,
                "accion": "INSERT",
                "tabla": "comunicados",
                "campo_desconocido_en_bd": "valor_extra",
                "detalle": {
                    "titulo": "Inicio de clases"
                }
            }
        """.trimIndent()

        val item = json.decodeFromString<Auditoria>(rawJson)
        assertEquals("999", item.id)
        assertEquals("Inicio de clases", formatearResumen(item.accion, item.detalle))
    }
}
