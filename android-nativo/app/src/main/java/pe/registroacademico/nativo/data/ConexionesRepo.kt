package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.model.ConexionDatos
import pe.registroacademico.nativo.data.model.NuevaConexionPayload
import javax.inject.Inject
import javax.inject.Singleton

interface ConexionesRepo {
    suspend fun listar(): List<ConexionDatos>
    suspend fun agregar(nombre: String, tipo: String, url: String, llavePublica: String): ConexionDatos
    suspend fun actualizar(id: String, cambios: Map<String, Any?>): Unit
    suspend fun marcarDestino(id: String, listaActual: List<ConexionDatos>): Unit
    suspend fun eliminar(id: String): Unit
    suspend fun leerTabla(tabla: String): List<JsonObject>
}

@Singleton
class ConexionesRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : ConexionesRepo {

    override suspend fun listar(): List<ConexionDatos> {
        return try {
            supabase.from("conexiones_datos").select {
                order("creado_en", Order.ASCENDING)
            }.decodeList<ConexionDatos>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) ||
                msg.contains("schema cache", ignoreCase = true) ||
                msg.contains("relation", ignoreCase = true)
            ) {
                throw ApiException("Falta aplicar la migración 017 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun agregar(
        nombre: String,
        tipo: String,
        url: String,
        llavePublica: String
    ): ConexionDatos {
        return try {
            val payload = NuevaConexionPayload(
                nombre = nombre.trim(),
                tipo = tipo.trim(),
                url = url.trim(),
                llavePublica = llavePublica.trim()
            )
            supabase.from("conexiones_datos").insert(payload) {
                select()
            }.decodeSingle<ConexionDatos>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) ||
                msg.contains("schema cache", ignoreCase = true) ||
                msg.contains("relation", ignoreCase = true)
            ) {
                throw ApiException("Falta aplicar la migración 017 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun actualizar(id: String, cambios: Map<String, Any?>): Unit {
        try {
            val payload = buildJsonObject {
                cambios.forEach { (k, v) ->
                    when (v) {
                        null -> put(k, null as String?)
                        is String -> put(k, v)
                        is Boolean -> put(k, v)
                        is Number -> put(k, v.toLong())
                        else -> put(k, v.toString())
                    }
                }
            }
            supabase.from("conexiones_datos").update(payload) {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) ||
                msg.contains("schema cache", ignoreCase = true) ||
                msg.contains("relation", ignoreCase = true)
            ) {
                throw ApiException("Falta aplicar la migración 017 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun marcarDestino(id: String, listaActual: List<ConexionDatos>): Unit {
        val objetivo = listaActual.find { it.id == id } ?: return
        val nuevoEstadoDestino = !objetivo.destino
        if (nuevoEstadoDestino) {
            // Desmarcar todos los demás para cumplir la restricción única conexiones_un_destino
            listaActual.filter { it.destino && it.id != id }.forEach { anterior ->
                actualizar(anterior.id, mapOf("destino" to false))
            }
        }
        actualizar(id, mapOf("destino" to nuevoEstadoDestino))
    }

    override suspend fun eliminar(id: String): Unit {
        try {
            supabase.from("conexiones_datos").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) ||
                msg.contains("schema cache", ignoreCase = true) ||
                msg.contains("relation", ignoreCase = true)
            ) {
                throw ApiException("Falta aplicar la migración 017 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun leerTabla(tabla: String): List<JsonObject> {
        val todas = mutableListOf<JsonObject>()
        var desde = 0L
        while (true) {
            val lote = try {
                supabase.from(tabla).select {
                    range(desde, desde + 999)
                }.decodeList<JsonObject>()
            } catch (e: Throwable) {
                val msg = e.message ?: ""
                if (msg.contains("does not exist", ignoreCase = true) ||
                    msg.contains("schema cache", ignoreCase = true) ||
                    msg.contains("relation", ignoreCase = true)
                ) {
                    emptyList()
                } else {
                    throw mapearError(e)
                }
            }
            todas.addAll(lote)
            if (lote.size < 1000) break
            desde += 1000
        }
        return todas
    }
}
