package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.model.PersonalDirectorioItem
import pe.registroacademico.nativo.data.model.PersonalItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

interface PersonalRepo {
    suspend fun personalListar(): List<PersonalItem>
    suspend fun personalAsignar(email: String, rol: String, carrera: String?, nombre: String?): Unit
    suspend fun personalQuitar(id: String): Unit
    suspend fun personalDirectorio(): List<PersonalDirectorioItem>?
    suspend fun actualizarMiPerfil(nombre: String?, path: String?): Unit
    suspend fun subirFotoPerfil(userId: String, bytes: ByteArray): String
    suspend fun fotoPersonalUrl(path: String): String?
}

@Singleton
class PersonalRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : PersonalRepo {

    override suspend fun personalListar(): List<PersonalItem> {
        return try {
            supabase.postgrest.rpc("personal_listar").decodeList<PersonalItem>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun personalAsignar(
        email: String,
        rol: String,
        carrera: String?,
        nombre: String?
    ): Unit {
        try {
            val params = buildJsonObject {
                put("p_email", email.trim().lowercase())
                put("p_rol", rol)
                if (carrera != null) put("p_carrera", carrera)
                if (nombre != null) put("p_nombre", nombre)
            }
            supabase.postgrest.rpc("personal_asignar", params)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun personalQuitar(id: String): Unit {
        try {
            val params = buildJsonObject {
                put("p_id", id)
            }
            supabase.postgrest.rpc("personal_quitar", params)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun personalDirectorio(): List<PersonalDirectorioItem>? {
        return try {
            supabase.postgrest.rpc("personal_directorio").decodeList<PersonalDirectorioItem>()
        } catch (e: Throwable) {
            null
        }
    }

    override suspend fun actualizarMiPerfil(nombre: String?, path: String?): Unit {
        try {
            val params = buildJsonObject {
                if (nombre != null) put("p_nombre", nombre)
                if (path != null) put("p_path", path)
            }
            supabase.postgrest.rpc("actualizar_mi_perfil", params)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun subirFotoPerfil(userId: String, bytes: ByteArray): String {
        try {
            val ruta = "$userId/foto-${System.currentTimeMillis()}.jpg"
            val bucket = supabase.storage.from("fotos-personal")
            bucket.upload(ruta, bytes) {
                upsert = true
            }
            actualizarMiPerfil(nombre = null, path = ruta)
            return ruta
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun fotoPersonalUrl(path: String): String? {
        if (path.isBlank()) return null
        return try {
            supabase.storage.from("fotos-personal").createSignedUrl(path, 1.hours)
        } catch (e: Throwable) {
            null
        }
    }
}
