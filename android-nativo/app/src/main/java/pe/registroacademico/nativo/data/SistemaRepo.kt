package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import pe.registroacademico.nativo.data.model.Auditoria
import pe.registroacademico.nativo.data.model.LogCliente
import javax.inject.Inject
import javax.inject.Singleton

interface SistemaRepo {
    suspend fun horaServidor(): Long
    suspend fun auditoriaLista(
        colegioId: String,
        limite: Long = 200,
        tabla: String? = null,
        accion: String? = null
    ): List<Auditoria>
    suspend fun erroresRecientes(colegioId: String, limite: Long = 200): List<LogCliente>
    suspend fun registrarError(log: LogCliente): Unit
    suspend fun borrarErrores(colegioId: String): Unit
    suspend fun exportarTodo(colegioId: String): Map<String, List<JsonObject>>
}

@Singleton
class SistemaRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : SistemaRepo {

    override suspend fun horaServidor(): Long {
        return try {
            val isoString = supabase.postgrest.rpc("hora_servidor").decodeAs<String>()
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }
    }

    override suspend fun auditoriaLista(
        colegioId: String,
        limite: Long,
        tabla: String?,
        accion: String?
    ): List<Auditoria> {
        return try {
            supabase.from("auditoria").select {
                filter {
                    eq("colegio_id", colegioId)
                    if (!tabla.isNullOrBlank()) eq("tabla", tabla)
                    if (!accion.isNullOrBlank()) eq("accion", accion)
                }
                order("creado_en", Order.DESCENDING)
                limit(limite)
            }.decodeList<Auditoria>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) ||
                msg.contains("schema cache", ignoreCase = true) ||
                msg.contains("relation", ignoreCase = true)
            ) {
                throw ApiException(
                    "Falta aplicar la migración 007 (supabase/migrations/007_operacion_avanzada.sql).",
                    e,
                    "migration007"
                )
            }
            throw mapearError(e)
        }
    }

    override suspend fun erroresRecientes(colegioId: String, limite: Long): List<LogCliente> {
        return try {
            supabase.from("logs_cliente").select {
                filter { eq("colegio_id", colegioId) }
                order("creado_en", Order.DESCENDING)
                limit(limite)
            }.decodeList<LogCliente>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun registrarError(log: LogCliente): Unit {
        try {
            supabase.from("logs_cliente").insert(log)
        } catch (e: Throwable) {
            // Ignorar para evitar bucles de errores
        }
    }

    override suspend fun borrarErrores(colegioId: String): Unit {
        try {
            supabase.from("logs_cliente").delete {
                filter { eq("colegio_id", colegioId) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun exportarTodo(colegioId: String): Map<String, List<JsonObject>> {
        val tablas = listOf(
            "alumnos", "niveles", "grados", "docentes", "comunicados",
            "cursos", "asistencias", "asistencias_curso", "justificaciones", "avisos_apoderados"
        )
        val resultado = mutableMapOf<String, List<JsonObject>>()
        for (tabla in tablas) {
            try {
                val filas = supabase.from(tabla).select {
                    filter { eq("colegio_id", colegioId) }
                    order("id", Order.ASCENDING)
                }.decodeList<JsonObject>()
                resultado[tabla] = filas
            } catch (e: Throwable) {
                resultado[tabla] = emptyList()
            }
        }
        return resultado
    }
}
