package pe.registroacademico.nativo.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.domain.Conectores
import pe.registroacademico.nativo.domain.TABLAS_CONEXION

class ConectoresTest {

    @Test
    fun clasificarLlave_llaveVacia_retornaInvalido() {
        val r1 = Conectores.clasificarLlave("")
        assertFalse(r1.ok)
        assertEquals("Falta la llave.", r1.motivo)

        val r2 = Conectores.clasificarLlave(null)
        assertFalse(r2.ok)
        assertEquals("Falta la llave.", r2.motivo)

        val r3 = Conectores.clasificarLlave("   ")
        assertFalse(r3.ok)
        assertEquals("Falta la llave.", r3.motivo)
    }

    @Test
    fun clasificarLlave_llaveConEspacios_retornaInvalido() {
        val r = Conectores.clasificarLlave("sb_publishable_123 456")
        assertFalse(r.ok)
        assertEquals("La llave no debe contener espacios.", r.motivo)
    }

    @Test
    fun clasificarLlave_llaveSecretaSupabase_retornaInvalido() {
        val r1 = Conectores.clasificarLlave("sb_secret_abcdef1234567890")
        assertFalse(r1.ok)
        assertEquals("Es una llave SECRETA de Supabase (sb_secret_). Usa solo la publishable/anon.", r1.motivo)

        val r2 = Conectores.clasificarLlave("SB_SECRET_xyz987654321")
        assertFalse(r2.ok)
        assertEquals("Es una llave SECRETA de Supabase (sb_secret_). Usa solo la publishable/anon.", r2.motivo)
    }

    @Test
    fun clasificarLlave_credencialPrivada_retornaInvalido() {
        val r1 = Conectores.clasificarLlave("-----BEGIN RSA PRIVATE KEY-----MIICXAIBAAKCAQ...")
        assertFalse(r1.ok)
        assertEquals("Es una credencial privada (cuenta de servicio). Nunca la pegues aquí.", r1.motivo)

        val r2 = Conectores.clasificarLlave("-----BEGIN PRIVATE KEY-----MIIEvgIBADANBgkqhki...")
        assertFalse(r2.ok)
        assertEquals("Es una credencial privada (cuenta de servicio). Nunca la pegues aquí.", r2.motivo)

        val r3 = Conectores.clasificarLlave("{\"type\":\"service_account\",\"project_id\":\"xyz\"}")
        assertFalse(r3.ok)
        assertEquals("Es una credencial privada (cuenta de servicio). Nunca la pegues aquí.", r3.motivo)
    }

    @Test
    fun clasificarLlave_jwtServiceRole_retornaInvalido() {
        // Payload: {"role":"service_role","iss":"supabase"}
        // Base64URL: eyJyb2xlIjoic2VydmljZV9yb2xlIiwiaXNzIjoic3VwYWJhc2UifQ
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val payload = "eyJyb2xlIjoic2VydmljZV9yb2xlIiwiaXNzIjoic3VwYWJhc2UifQ"
        val signature = "dummy_signature_123"
        val jwt = "$header.$payload.$signature"

        val r = Conectores.clasificarLlave(jwt)
        assertFalse(r.ok)
        assertEquals("Es la llave service_role, que salta toda la seguridad. Usa la anon/publishable.", r.motivo)
    }

    @Test
    fun clasificarLlave_jwtSupabaseAdmin_retornaInvalido() {
        // Payload: {"role":"supabase_admin"}
        // Base64URL: eyJyb2xlIjoic3VwYWJhc2VfYWRtaW4ifQ
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val payload = "eyJyb2xlIjoic3VwYWJhc2VfYWRtaW4ifQ"
        val signature = "signature"
        val jwt = "$header.$payload.$signature"

        val r = Conectores.clasificarLlave(jwt)
        assertFalse(r.ok)
        assertEquals("Es la llave service_role, que salta toda la seguridad. Usa la anon/publishable.", r.motivo)
    }

    @Test
    fun clasificarLlave_publishableKey_retornaValido() {
        val r = Conectores.clasificarLlave("sb_publishable_V0wt4JdNVXHc20_0tJWBHA_D47gcmbf")
        assertTrue(r.ok)
        assertNull(r.motivo)
    }

    @Test
    fun clasificarLlave_jwtAnon_retornaValido() {
        // Payload: {"role":"anon","iss":"supabase"}
        // Base64URL: eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIn0
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val payload = "eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIn0"
        val signature = "valid_signature_abc"
        val jwt = "$header.$payload.$signature"

        val r = Conectores.clasificarLlave(jwt)
        assertTrue(r.ok)
        assertNull(r.motivo)
    }

    @Test
    fun normalizarDestino_firestoreValido_retornaValido() {
        val r = Conectores.normalizarDestino("firestore", "mi-instituto-12345")
        assertTrue(r.ok)
        assertEquals("mi-instituto-12345", r.valor)
    }

    @Test
    fun normalizarDestino_firestoreInvalido_retornaInvalido() {
        val r1 = Conectores.normalizarDestino("firestore", "123-invalido")
        assertFalse(r1.ok)
        assertEquals("El ID del proyecto Firebase son 6–30 letras minúsculas, números o guiones.", r1.motivo)

        val r2 = Conectores.normalizarDestino("firestore", "MAYUSCULAS-ABC")
        assertFalse(r2.ok)

        val r3 = Conectores.normalizarDestino("firestore", "ab")
        assertFalse(r3.ok)
    }

    @Test
    fun normalizarDestino_supabaseHttps_remueveBarrasFinales() {
        val r = Conectores.normalizarDestino("supabase", "https://xxxx.supabase.co///")
        assertTrue(r.ok)
        assertEquals("https://xxxx.supabase.co", r.valor)
    }

    @Test
    fun normalizarDestino_localhostHttp_retornaValido() {
        val r1 = Conectores.normalizarDestino("rest", "http://localhost:54321/lummer")
        assertTrue(r1.ok)
        assertEquals("http://localhost:54321/lummer", r1.valor)

        val r2 = Conectores.normalizarDestino("supabase", "http://127.0.0.1:8000/")
        assertTrue(r2.ok)
        assertEquals("http://127.0.0.1:8000", r2.valor)
    }

    @Test
    fun normalizarDestino_httpNoLocalhost_retornaInvalido() {
        val r = Conectores.normalizarDestino("supabase", "http://api.externa.com")
        assertFalse(r.ok)
        assertEquals("La URL debe empezar con https:// (http solo para localhost).", r.motivo)
    }

    @Test
    fun idDeFila_cursoDocentes_generaClaveCompuesta() {
        val fila = buildJsonObject {
            put("curso_id", "c123")
            put("user_id", "u456")
        }
        val id = Conectores.idDeFila("curso_docentes", fila, 0)
        assertEquals("c123_u456", id)
    }

    @Test
    fun idDeFila_cursoAlumnos_generaClaveCompuesta() {
        val fila = buildJsonObject {
            put("curso_id", "c123")
            put("alumno_id", "a789")
        }
        val id = Conectores.idDeFila("curso_alumnos", fila, 0)
        assertEquals("c123_a789", id)
        assertTrue(TABLAS_CONEXION.contains("curso_alumnos"))
    }

    @Test
    fun idDeFila_conId_retornaId() {
        val fila = buildJsonObject {
            put("id", "uuid-alumno-789")
            put("nombre", "Juan Perez")
        }
        val id = Conectores.idDeFila("alumnos", fila, 0)
        assertEquals("uuid-alumno-789", id)
    }

    @Test
    fun idDeFila_sinId_retornaFallback() {
        val fila = buildJsonObject {
            put("campo", "valor")
        }
        val id = Conectores.idDeFila("alumnos", fila, 42)
        assertEquals("alumnos-42", id)
    }

    @Test
    fun aFirestore_convierteTiposCorrectamente() {
        val n = Conectores.aFirestore(JsonNull).jsonObject
        assertTrue(n.containsKey("nullValue"))

        val b = Conectores.aFirestore(JsonPrimitive(true)).jsonObject
        assertEquals("true", b["booleanValue"]?.jsonPrimitive?.content)

        val i = Conectores.aFirestore(JsonPrimitive(1234L)).jsonObject
        assertEquals("1234", i["integerValue"]?.jsonPrimitive?.content)

        val s = Conectores.aFirestore(JsonPrimitive("hola mundo")).jsonObject
        assertEquals("hola mundo", s["stringValue"]?.jsonPrimitive?.content)
    }

    @Test
    fun lotes_divideListaCorrectamente() {
        val items = (1..10).toList()
        val chunked = Conectores.lotes(items, 3)
        assertEquals(4, chunked.size)
        assertEquals(listOf(1, 2, 3), chunked[0])
        assertEquals(listOf(4, 5, 6), chunked[1])
        assertEquals(listOf(7, 8, 9), chunked[2])
        assertEquals(listOf(10), chunked[3])
    }

    @Test
    fun cabecerasSupabase_llavePublishable_soloApikey() {
        val headers = Conectores.cabecerasSupabase("sb_publishable_test123")
        assertEquals(1, headers.size)
        assertEquals("sb_publishable_test123", headers["apikey"])
        assertFalse(headers.containsKey("Authorization"))
    }

    @Test
    fun cabecerasSupabase_jwtAnon_apikeyYAuthorization() {
        val headers = Conectores.cabecerasSupabase("eyJhbGciOiJI...")
        assertEquals(2, headers.size)
        assertEquals("eyJhbGciOiJI...", headers["apikey"])
        assertEquals("Bearer eyJhbGciOiJI...", headers["Authorization"])
    }
}
