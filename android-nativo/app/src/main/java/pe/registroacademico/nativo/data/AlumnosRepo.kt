package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.model.Alumno
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

interface AlumnosRepo {
    suspend fun listar(colegioId: String): List<Alumno>
    suspend fun obtenerPorId(id: String): Alumno?
    suspend fun obtenerPorCodigo(colegioId: String, codigo: String): Alumno?
    suspend fun guardar(alumno: Alumno): Unit
    suspend fun eliminar(id: String): Unit
    suspend fun upsertAlumnos(rows: List<Alumno>): Int
    suspend fun listarSolicitudes(colegioId: String): List<Alumno>
    suspend fun aprobarSolicitud(id: String, colegioId: String): Unit
    suspend fun rechazarSolicitud(id: String): Unit
    suspend fun fotoUrl(fotoPath: String): String?
    suspend fun eliminarFotoAlumno(fotoPath: String): Unit
}

@Singleton
class AlumnosRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AlumnosRepo {

    override suspend fun listar(colegioId: String): List<Alumno> {
        return try {
            supabase.from("alumnos").select {
                filter {
                    eq("colegio_id", colegioId)
                }
                order("nombre", Order.ASCENDING)
            }.decodeList<Alumno>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun obtenerPorId(id: String): Alumno? {
        return try {
            supabase.from("alumnos").select {
                filter {
                    eq("id", id)
                }
            }.decodeSingleOrNull<Alumno>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun obtenerPorCodigo(colegioId: String, codigo: String): Alumno? {
        return try {
            supabase.from("alumnos").select {
                filter {
                    eq("colegio_id", colegioId)
                    eq("codigo", codigo)
                }
            }.decodeSingleOrNull<Alumno>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun guardar(alumno: Alumno): Unit {
        try {
            if (alumno.id.isNotBlank()) {
                val existe = obtenerPorId(alumno.id)
                if (existe != null) {
                    supabase.from("alumnos").update(alumno) {
                        filter {
                            eq("id", alumno.id)
                        }
                    }
                } else {
                    supabase.from("alumnos").insert(alumno)
                }
            } else {
                supabase.from("alumnos").insert(alumno)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminar(id: String): Unit {
        try {
            supabase.from("alumnos").delete {
                filter {
                    eq("id", id)
                }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun upsertAlumnos(rows: List<Alumno>): Int {
        try {
            var n = 0
            for (i in rows.indices step 200) {
                val lote = rows.subList(i, minOf(i + 200, rows.size))
                supabase.from("alumnos").upsert(lote) {
                    onConflict = "colegio_id,codigo"
                }
                n += lote.size
            }
            return n
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarSolicitudes(colegioId: String): List<Alumno> {
        return try {
            supabase.from("alumnos").select {
                filter {
                    eq("colegio_id", colegioId)
                    eq("aprobado", false)
                }
                order("nombre", Order.ASCENDING)
            }.decodeList<Alumno>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun aprobarSolicitud(id: String, colegioId: String): Unit {
        try {
            val updatePayload = buildJsonObject {
                put("aprobado", true)
            }
            supabase.from("alumnos").update(updatePayload) {
                filter {
                    eq("id", id)
                }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun rechazarSolicitud(id: String): Unit {
        eliminar(id)
    }

    override suspend fun fotoUrl(fotoPath: String): String? {
        if (fotoPath.isBlank()) return null
        return try {
            supabase.storage.from("fotos-alumnos").createSignedUrl(fotoPath, 1.hours)
        } catch (e: Throwable) {
            null
        }
    }

    override suspend fun eliminarFotoAlumno(fotoPath: String): Unit {
        if (fotoPath.isBlank()) return
        try {
            supabase.storage.from("fotos-alumnos").delete(fotoPath)
        } catch (e: Throwable) {
            // Ignorar errores al eliminar fotos no existentes
        }
    }
}
