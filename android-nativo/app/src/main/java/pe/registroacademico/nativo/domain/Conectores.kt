package pe.registroacademico.nativo.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class TipoConexion(
    val valor: String,
    val etiqueta: String,
    val urlEtiqueta: String,
    val urlEjemplo: String,
    val llaveEtiqueta: String
) {
    SUPABASE(
        valor = "supabase",
        etiqueta = "Supabase / Postgres (PostgREST)",
        urlEtiqueta = "URL del proyecto",
        urlEjemplo = "https://xxxx.supabase.co",
        llaveEtiqueta = "Llave pública (publishable / anon)"
    ),
    REST(
        valor = "rest",
        etiqueta = "API REST (MySQL, MariaDB, MongoDB u otra)",
        urlEtiqueta = "URL base de la API",
        urlEjemplo = "https://api.tu-dominio.com/lummer",
        llaveEtiqueta = "Token de acceso de la API"
    ),
    FIRESTORE(
        valor = "firestore",
        etiqueta = "Firebase / Firestore",
        urlEtiqueta = "ID del proyecto Firebase",
        urlEjemplo = "mi-instituto-12345",
        llaveEtiqueta = "API key web de Firebase"
    );

    companion object {
        fun desdeValor(valor: String?): TipoConexion {
            return when (valor?.trim()?.lowercase()) {
                "rest" -> REST
                "firestore" -> FIRESTORE
                else -> SUPABASE
            }
        }
    }
}

val TABLAS_CONEXION = listOf(
    "colegios", "niveles", "grados", "docentes", "alumnos", "comunicados", "cursos", "curso_docentes",
    "asistencias", "justificaciones", "avisos_apoderados", "asistencias_curso",
    "curso_materiales", "curso_actividades", "curso_entregas"
)

private val CLAVE_ID = mapOf(
    "curso_docentes" to listOf("curso_id", "user_id")
)

data class ClasificacionLlave(
    val ok: Boolean,
    val motivo: String? = null
)

data class NormalizacionDestino(
    val ok: Boolean,
    val valor: String,
    val motivo: String? = null
)

data class PruebaConexionResultado(
    val ok: Boolean,
    val ms: Long,
    val detalle: String
)

data class InformeTabla(
    val tabla: String,
    val enviadas: Int,
    val error: String? = null,
    val conteoDestino: Int? = null,
    val esperadas: Int = 0
)

@Serializable
data class PaqueteLummer(
    val formato: String = "lummer-paquete",
    val version: Int = 1,
    val creado: String,
    val tablas: Map<String, List<JsonObject>>,
    val conteos: Map<String, Int>,
    val avisos: List<String>
)

object Conectores {

    private val REGEX_SB_SECRET = Regex("""^sb_secret_""", RegexOption.IGNORE_CASE)
    private val REGEX_CRED_PRIVADA = Regex(
        """BEGIN (RSA |EC )?PRIVATE KEY|"private_key"|"type"\s*:\s*"service_account"""",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_FIRESTORE_ID = Regex("""^[a-z][a-z0-9-]{4,29}$""")
    private val REGEX_URL_HTTPS = Regex("""^https://[^\s/]+(/.*)?$""")
    private val REGEX_URL_LOCALHOST = Regex("""^http://(localhost|127\.0\.0\.1)(:\d+)?(/.*)?$""")
    private val REGEX_ESPACIOS = Regex("""\s""")

    fun decodeBase64Url(str: String): String? {
        return try {
            val clean = str.trim().replace('-', '+').replace('_', '/')
            val pad = (4 - clean.length % 4) % 4
            val padded = clean + "=".repeat(pad)
            val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val bytes = ArrayList<Byte>()
            var buffer = 0
            var bits = 0
            for (c in padded) {
                if (c == '=') break
                val idx = table.indexOf(c)
                if (idx < 0) return null
                buffer = (buffer shl 6) or idx
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    bytes.add(((buffer shr bits) and 0xFF).toByte())
                }
            }
            String(bytes.toByteArray(), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * ¿Se puede guardar esta llave? Rechaza llaves secretas.
     */
    fun clasificarLlave(llave: String?): ClasificacionLlave {
        val k = llave?.trim().orEmpty()
        if (k.isEmpty()) {
            return ClasificacionLlave(ok = false, motivo = "Falta la llave.")
        }
        if (REGEX_SB_SECRET.containsMatchIn(k)) {
            return ClasificacionLlave(
                ok = false,
                motivo = "Es una llave SECRETA de Supabase (sb_secret_). Usa solo la publishable/anon."
            )
        }
        if (REGEX_CRED_PRIVADA.containsMatchIn(k)) {
            return ClasificacionLlave(
                ok = false,
                motivo = "Es una credencial privada (cuenta de servicio). Nunca la pegues aquí."
            )
        }
        val partes = k.split(".")
        if (partes.size == 3) {
            val payload = decodeBase64Url(partes[1])
            if (payload != null && Regex("""service_role|supabase_admin""", RegexOption.IGNORE_CASE).containsMatchIn(payload)) {
                return ClasificacionLlave(
                    ok = false,
                    motivo = "Es la llave service_role, que salta toda la seguridad. Usa la anon/publishable."
                )
            }
        }
        if (REGEX_ESPACIOS.containsMatchIn(k)) {
            return ClasificacionLlave(ok = false, motivo = "La llave no debe contener espacios.")
        }
        return ClasificacionLlave(ok = true)
    }

    /**
     * Normaliza y valida la URL o el ID según el tipo.
     */
    fun normalizarDestino(tipo: String, url: String?): NormalizacionDestino {
        val v = url?.trim().orEmpty()
        val t = tipo.trim().lowercase()
        if (t == "firestore") {
            return if (REGEX_FIRESTORE_ID.matches(v)) {
                NormalizacionDestino(ok = true, valor = v)
            } else {
                NormalizacionDestino(
                    ok = false,
                    valor = v,
                    motivo = "El ID del proyecto Firebase son 6–30 letras minúsculas, números o guiones."
                )
            }
        }
        val sinBarra = v.replace(Regex("""/+$"""), "")
        if (!REGEX_URL_HTTPS.matches(sinBarra) && !REGEX_URL_LOCALHOST.matches(sinBarra)) {
            return NormalizacionDestino(
                ok = false,
                valor = v,
                motivo = "La URL debe empezar con https:// (http solo para localhost)."
            )
        }
        return NormalizacionDestino(ok = true, valor = sinBarra)
    }

    fun idDeFila(tabla: String, fila: JsonObject, i: Int): String {
        val claves = CLAVE_ID[tabla]
        if (claves != null) {
            return claves.mapNotNull { fila[it]?.jsonPrimitive?.content }.joinToString("_")
        }
        val idVal = fila["id"]?.jsonPrimitive?.content
        return if (!idVal.isNullOrBlank()) idVal else "$tabla-$i"
    }

    fun aFirestore(v: JsonElement): JsonElement {
        return when (v) {
            is JsonNull -> buildJsonObject { put("nullValue", JsonNull) }
            is JsonPrimitive -> {
                val boolVal = v.booleanOrNull
                if (boolVal != null) {
                    buildJsonObject { put("booleanValue", boolVal) }
                } else {
                    val intVal = v.longOrNull
                    if (intVal != null) {
                        buildJsonObject { put("integerValue", intVal.toString()) }
                    } else {
                        val dblVal = v.doubleOrNull
                        if (dblVal != null) {
                            buildJsonObject { put("doubleValue", dblVal) }
                        } else {
                            buildJsonObject { put("stringValue", v.content) }
                        }
                    }
                }
            }
            is JsonArray -> {
                buildJsonObject {
                    put("arrayValue", buildJsonObject {
                        put("values", buildJsonArray {
                            v.forEach { add(aFirestore(it)) }
                        })
                    })
                }
            }
            is JsonObject -> {
                buildJsonObject {
                    put("mapValue", buildJsonObject {
                        put("fields", buildJsonObject {
                            for ((k, x) in v) {
                                put(k, aFirestore(x))
                            }
                        })
                    })
                }
            }
        }
    }

    fun <T> lotes(lista: List<T>, n: Int): List<List<T>> {
        if (n <= 0) return listOf(lista)
        val r = mutableListOf<List<T>>()
        var i = 0
        while (i < lista.size) {
            r.add(lista.subList(i, (i + n).coerceAtMost(lista.size)))
            i += n
        }
        return r
    }

    fun cabecerasSupabase(llave: String): Map<String, String> {
        return if (llave.startsWith("sb_")) {
            mapOf("apikey" to llave)
        } else {
            mapOf("apikey" to llave, "Authorization" to "Bearer $llave")
        }
    }
}

object BasePropiaStore {
    private const val PREFS_NAME = "ra_base_config"
    private const val KEY_URL = "custom_supabase_url"
    private const val KEY_ANON = "custom_supabase_anon_key"

    fun getOverride(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, null)?.trim()?.removeSuffix("/") ?: return null
        val key = prefs.getString(KEY_ANON, null)?.trim() ?: return null
        if (url.isEmpty() || key.isEmpty()) return null
        return Pair(url, key)
    }

    fun guardarOverride(context: Context, url: String, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_URL, url.trim().removeSuffix("/"))
            .putString(KEY_ANON, key.trim())
            .apply()
    }

    fun borrarOverride(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_URL).remove(KEY_ANON).apply()
    }

    fun getBaseActiva(context: Context, urlOriginal: String): Pair<String, Boolean> {
        val override = getOverride(context)
        return if (override != null) {
            Pair(override.first, true)
        } else {
            Pair(urlOriginal, false)
        }
    }
}

object ConectoresHttp {

    private fun baseFirestore(id: String) =
        "https://firestore.googleapis.com/v1/projects/$id/databases/(default)/documents"

    suspend fun probarConexion(tipo: String, url: String, llave: String): PruebaConexionResultado =
        withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            val t = tipo.trim().lowercase()
            try {
                if (t == "supabase") {
                    val urlObj = URL("$url/rest/v1/")
                    val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        instanceFollowRedirects = true
                        Conectores.cabecerasSupabase(llave).forEach { (k, v) ->
                            setRequestProperty(k, v)
                        }
                    }
                    val status = conn.responseCode
                    val ms = System.currentTimeMillis() - t0
                    if (status in 200..299) {
                        return@withContext PruebaConexionResultado(ok = true, ms = ms, detalle = "Conexión correcta.")
                    }
                    if (status == 401 || status == 403) {
                        val cuerpo = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        val regexRechazo = Regex("""invalid (api key|jwt)|no api key""", RegexOption.IGNORE_CASE)
                        return@withContext if (regexRechazo.containsMatchIn(cuerpo) || cuerpo.isBlank()) {
                            PruebaConexionResultado(
                                ok = false,
                                ms = ms,
                                detalle = "El servidor respondió, pero rechazó la llave (revisa que sea la llave publishable/anon de ESTE proyecto, completa y sin espacios)."
                            )
                        } else {
                            PruebaConexionResultado(
                                ok = true,
                                ms = ms,
                                detalle = "Llave aceptada (el servidor no permite listar el esquema con una llave pública, es normal)."
                            )
                        }
                    }
                    return@withContext PruebaConexionResultado(
                        ok = false,
                        ms = ms,
                        detalle = "El servidor respondió $status."
                    )
                }

                if (t == "rest") {
                    val urlObj = URL("$url/salud")
                    val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        setRequestProperty("Authorization", "Bearer $llave")
                    }
                    val status = conn.responseCode
                    val ms = System.currentTimeMillis() - t0
                    if (status in 200..299) {
                        return@withContext PruebaConexionResultado(ok = true, ms = ms, detalle = "La API responde en /salud.")
                    }
                    if (status == 401 || status == 403) {
                        return@withContext PruebaConexionResultado(ok = false, ms = ms, detalle = "La API respondió, pero rechazó el token.")
                    }
                    return@withContext PruebaConexionResultado(
                        ok = false,
                        ms = ms,
                        detalle = "La API respondió $status en /salud."
                    )
                }

                // Firestore
                val encKey = URLEncoder.encode(llave, "UTF-8")
                val urlObj = URL("${baseFirestore(url)}?pageSize=1&key=$encKey")
                val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }
                val status = conn.responseCode
                val ms = System.currentTimeMillis() - t0
                if (status in 200..299) {
                    return@withContext PruebaConexionResultado(ok = true, ms = ms, detalle = "Firestore responde y las reglas permiten leer.")
                }
                if (status == 403) {
                    return@withContext PruebaConexionResultado(ok = true, ms = ms, detalle = "Firestore responde; sus reglas bloquean lectura anónima (revisa las reglas para poder importar).")
                }
                if (status == 400) {
                    return@withContext PruebaConexionResultado(ok = false, ms = ms, detalle = "API key de Firebase inválida.")
                }
                return@withContext PruebaConexionResultado(ok = false, ms = ms, detalle = "Firestore respondió $status.")
            } catch (e: Throwable) {
                val ms = System.currentTimeMillis() - t0
                val msg = e.message ?: "error de red"
                return@withContext PruebaConexionResultado(
                    ok = false,
                    ms = ms,
                    detalle = "No se pudo conectar ($msg). ¿Está bien la URL y permite CORS?"
                )
            }
        }

    suspend fun contarEnDestino(tipo: String, url: String, llave: String, tabla: String): Int? =
        withContext(Dispatchers.IO) {
            val t = tipo.trim().lowercase()
            try {
                if (t == "supabase") {
                    val urlObj = URL("$url/rest/v1/$tabla?select=*")
                    val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                        requestMethod = "HEAD"
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        setRequestProperty("Prefer", "count=exact")
                        setRequestProperty("Range", "0-0")
                        Conectores.cabecerasSupabase(llave).forEach { (k, v) ->
                            setRequestProperty(k, v)
                        }
                    }
                    val range = conn.getHeaderField("Content-Range") ?: conn.getHeaderField("content-range")
                    if (!range.isNullOrBlank()) {
                        val m = Regex("""/(\d+|\*)$""").find(range)
                        val totalStr = m?.groupValues?.get(1)
                        if (totalStr != null && totalStr != "*") {
                            return@withContext totalStr.toIntOrNull()
                        }
                    }
                    return@withContext null
                }
                if (t == "rest") {
                    val urlObj = URL("$url/conteo/$tabla")
                    val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        setRequestProperty("Authorization", "Bearer $llave")
                    }
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = Json.parseToJsonElement(body).jsonObject
                        return@withContext json["total"]?.jsonPrimitive?.intOrNull
                    }
                    return@withContext null
                }
            } catch (_: Throwable) {
                // No verificable
            }
            null
        }

    suspend fun enviarLote(
        tipo: String,
        url: String,
        llave: String,
        tabla: String,
        grupo: List<JsonObject>,
        enviadasOffset: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val t = tipo.trim().lowercase()
        try {
            val (status, errorBody) = when (t) {
                "supabase" -> {
                    val urlObj = URL("$url/rest/v1/$tabla")
                    val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 30_000
                        readTimeout = 60_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                        Conectores.cabecerasSupabase(llave).forEach { (k, v) ->
                            setRequestProperty(k, v)
                        }
                    }
                    val jsonArrayStr = JsonArray(grupo).toString()
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonArrayStr) }
                    val code = conn.responseCode
                    val err = if (code !in 200..299) conn.errorStream?.bufferedReader()?.use { it.readText() } else null
                    Pair(code, err)
                }

                "rest" -> {
                    val urlObj = URL("$url/importar/$tabla")
                    val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 30_000
                        readTimeout = 60_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $llave")
                    }
                    val bodyStr = buildJsonObject {
                        put("filas", JsonArray(grupo))
                    }.toString()
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(bodyStr) }
                    val code = conn.responseCode
                    val err = if (code !in 200..299) conn.errorStream?.bufferedReader()?.use { it.readText() } else null
                    Pair(code, err)
                }

                else -> {
                    // Firestore commit
                    val encKey = URLEncoder.encode(llave, "UTF-8")
                    val urlObj = URL("${baseFirestore(url)}:commit?key=$encKey")
                    val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 30_000
                        readTimeout = 60_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                    val writes = buildJsonArray {
                        grupo.forEachIndexed { i, fila ->
                            val docId = Conectores.idDeFila(tabla, fila, enviadasOffset + i)
                            val docPath = "projects/$url/databases/(default)/documents/$tabla/$docId"
                            val fields = buildJsonObject {
                                for ((k, v) in fila) {
                                    put(k, Conectores.aFirestore(v))
                                }
                            }
                            add(buildJsonObject {
                                put("update", buildJsonObject {
                                    put("name", docPath)
                                    put("fields", fields)
                                })
                            })
                        }
                    }
                    val commitPayload = buildJsonObject {
                        put("writes", writes)
                    }.toString()
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(commitPayload) }
                    val code = conn.responseCode
                    val err = if (code !in 200..299) conn.errorStream?.bufferedReader()?.use { it.readText() } else null
                    Pair(code, err)
                }
            }

            if (status in 200..299) {
                Result.success(Unit)
            } else {
                val errorMsg = if (status == 401 || status == 403) {
                    "El destino rechazó la escritura (permisos/RLS). Crea el esquema en el destino y habilita la escritura para esta llave."
                } else {
                    "El destino respondió $status."
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Throwable) {
            val msg = e.message ?: "error de red"
            Result.failure(Exception("Sin conexión con el destino ($msg)."))
        }
    }
}
