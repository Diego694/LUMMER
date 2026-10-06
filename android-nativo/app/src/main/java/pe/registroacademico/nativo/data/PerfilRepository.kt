package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import pe.registroacademico.nativo.data.model.Colegio
import pe.registroacademico.nativo.data.model.Perfil
import pe.registroacademico.nativo.data.model.PerfilDetalle
import javax.inject.Inject
import javax.inject.Singleton

interface PerfilRepository {
    suspend fun getPerfil(userId: String): Result<Perfil?>
    suspend fun getColegio(colegioId: String): Result<Colegio?>
    suspend fun getColegio(colegioId: Long): Result<Colegio?> = getColegio(colegioId.toString())
    suspend fun getPerfilDetalle(userId: String): Result<PerfilDetalle>
    suspend fun esSuperadmin(): Boolean
}

@Singleton
class PerfilRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : PerfilRepository {

    override suspend fun getPerfil(userId: String): Result<Perfil?> {
        return runCatching {
            val list = supabase.from("perfiles").select {
                filter {
                    eq("id", userId)
                }
            }.decodeList<Perfil>()
            list.firstOrNull()
        }
    }

    override suspend fun getColegio(colegioId: String): Result<Colegio?> {
        return runCatching {
            val list = supabase.from("colegios").select {
                filter {
                    eq("id", colegioId)
                }
            }.decodeList<Colegio>()
            list.firstOrNull()
        }
    }

    override suspend fun getPerfilDetalle(userId: String): Result<PerfilDetalle> {
        return runCatching {
            val perfil = getPerfil(userId).getOrThrow()
                ?: throw ApiException("No se encontró un perfil de instituto para esta cuenta.")

            val cid = perfil.colegioId ?: ""
            val colegio = if (cid.isNotBlank()) getColegio(cid).getOrNull() else null
            val esSuper = esSuperadmin()

            PerfilDetalle(
                colegioId = cid,
                rol = perfil.rol ?: "Docente",
                nombre = perfil.nombre,
                carrera = perfil.carrera,
                fotoPath = perfil.fotoPath,
                colegio = colegio?.nombre ?: perfil.colegios?.nombre ?: "LUMMER",
                superadmin = esSuper,
                qrModo = colegio?.qrModo ?: perfil.colegios?.qrModo ?: "off"
            )
        }
    }

    override suspend fun esSuperadmin(): Boolean {
        return try {
            supabase.postgrest.rpc("es_superadmin").decodeAs<Boolean>()
        } catch (e: Exception) {
            false
        }
    }
}
