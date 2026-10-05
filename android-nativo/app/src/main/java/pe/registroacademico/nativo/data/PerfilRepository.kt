package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import pe.registroacademico.nativo.data.model.Colegio
import pe.registroacademico.nativo.data.model.Perfil
import javax.inject.Inject
import javax.inject.Singleton

interface PerfilRepository {
    suspend fun getPerfil(userId: String): Result<Perfil?>
    suspend fun getColegio(colegioId: Long): Result<Colegio?>
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

    override suspend fun getColegio(colegioId: Long): Result<Colegio?> {
        return runCatching {
            val list = supabase.from("colegios").select {
                filter {
                    eq("id", colegioId)
                }
            }.decodeList<Colegio>()
            list.firstOrNull()
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
