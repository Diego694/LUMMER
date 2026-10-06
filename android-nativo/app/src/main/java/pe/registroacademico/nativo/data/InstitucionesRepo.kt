package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.model.Colegio
import pe.registroacademico.nativo.data.model.InstitucionItem
import javax.inject.Inject
import javax.inject.Singleton

interface InstitucionesRepo {
    suspend fun esSuperadmin(): Boolean
    suspend fun saListar(): List<InstitucionItem>
    suspend fun saCrear(nombre: String, codigo: String? = null): InstitucionItem?
    suspend fun saRenombrar(id: String, nombre: String): Unit
    suspend fun saActivar(id: String, activo: Boolean): Unit
    suspend fun saEntrar(id: String): Unit
    suspend fun saAsignarAdmin(id: String, email: String): Unit
    suspend fun renombrarInstituto(colegioId: String, nombre: String): Unit
    suspend fun guardarQrModo(colegioId: String, modo: String): Unit
    suspend fun getCodigoRegistro(colegioId: String): String?
    suspend fun setCodigoRegistro(colegioId: String, codigo: String): Unit
}

@Singleton
class InstitucionesRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : InstitucionesRepo {

    override suspend fun esSuperadmin(): Boolean {
        return try {
            supabase.postgrest.rpc("es_superadmin").decodeAs<Boolean>()
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun saListar(): List<InstitucionItem> {
        return try {
            supabase.postgrest.rpc("sa_listar").decodeList<InstitucionItem>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException("Falta aplicar la migración 011 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun saCrear(nombre: String, codigo: String?): InstitucionItem? {
        val nom = nombre.trim()
        if (nom.length < 3 || nom.length > 80) {
            throw ApiException("El nombre debe tener entre 3 y 80 caracteres.")
        }
        return try {
            val params = buildJsonObject {
                put("p_nombre", nom)
                if (codigo != null) put("p_codigo", codigo)
            }
            supabase.postgrest.rpc("sa_crear", params).decodeSingleOrNull<InstitucionItem>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException("Falta aplicar la migración 011 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun saRenombrar(id: String, nombre: String): Unit {
        val nom = nombre.trim()
        if (nom.length < 3 || nom.length > 80) {
            throw ApiException("El nombre debe tener entre 3 y 80 caracteres.")
        }
        try {
            val params = buildJsonObject {
                put("p_id", id)
                put("p_nombre", nom)
            }
            supabase.postgrest.rpc("sa_renombrar", params)
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException("Falta aplicar la migración 011 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun saActivar(id: String, activo: Boolean): Unit {
        try {
            val params = buildJsonObject {
                put("p_id", id)
                put("p_activo", activo)
            }
            supabase.postgrest.rpc("sa_activar", params)
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException("Falta aplicar la migración 011 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun saEntrar(id: String): Unit {
        try {
            val params = buildJsonObject {
                put("p_colegio", id)
            }
            supabase.postgrest.rpc("sa_entrar", params)
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException("Falta aplicar la migración 011 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun saAsignarAdmin(id: String, email: String): Unit {
        try {
            val params = buildJsonObject {
                put("p_colegio", id)
                put("p_email", email.trim().lowercase())
            }
            supabase.postgrest.rpc("sa_asignar_admin", params)
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException("Falta aplicar la migración 011 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun renombrarInstituto(colegioId: String, nombre: String): Unit {
        val nom = nombre.trim()
        if (nom.length < 3 || nom.length > 80) {
            throw ApiException("El nombre debe tener entre 3 y 80 caracteres.")
        }
        try {
            val updatePayload = buildJsonObject {
                put("nombre", nom)
            }
            supabase.from("colegios").update(updatePayload) {
                filter { eq("id", colegioId) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun guardarQrModo(colegioId: String, modo: String): Unit {
        val m = modo.trim().lowercase()
        if (m !in listOf("off", "opcional", "obligatorio")) {
            throw ApiException("Modo no válido (usa off, opcional u obligatorio).")
        }
        try {
            val updatePayload = buildJsonObject {
                put("qr_modo", m)
            }
            supabase.from("colegios").update(updatePayload) {
                filter { eq("id", colegioId) }
            }
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("column", ignoreCase = true)) {
                throw ApiException("Falta aplicar la migración 012 en Supabase.", e)
            }
            throw mapearError(e)
        }
    }

    override suspend fun getCodigoRegistro(colegioId: String): String? {
        return try {
            val col = supabase.from("colegios").select {
                filter { eq("id", colegioId) }
            }.decodeSingleOrNull<Colegio>()
            col?.codigoRegistro
        } catch (e: Throwable) {
            null
        }
    }

    override suspend fun setCodigoRegistro(colegioId: String, codigo: String): Unit {
        try {
            val updatePayload = buildJsonObject {
                put("codigo_registro", codigo.trim().uppercase())
            }
            supabase.from("colegios").update(updatePayload) {
                filter { eq("id", colegioId) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }
}
