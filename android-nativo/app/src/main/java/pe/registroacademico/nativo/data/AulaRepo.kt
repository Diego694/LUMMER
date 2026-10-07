package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.ArchivoAula
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoActividad
import pe.registroacademico.nativo.data.model.CursoAlumno
import pe.registroacademico.nativo.data.model.CursoDocente
import pe.registroacademico.nativo.data.model.CursoEntrega
import pe.registroacademico.nativo.data.model.CursoMaterial
import pe.registroacademico.nativo.data.model.PersonalItem
import pe.registroacademico.nativo.domain.AulaUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

interface AulaRepo {
    suspend fun listarCursos(colegioId: String): List<Curso>
    suspend fun listarMateriales(cursoId: String): List<CursoMaterial>
    suspend fun guardarMaterial(material: CursoMaterial): Unit
    suspend fun eliminarMaterial(id: String, archivoPath: String?): Unit

    suspend fun listarActividades(cursoId: String): List<CursoActividad>
    suspend fun guardarActividad(actividad: CursoActividad): Unit
    suspend fun eliminarActividad(id: String, archivoPath: String?): Unit

    suspend fun subirArchivoAula(colegioId: String, cursoId: String, nombre: String, bytes: ByteArray): ArchivoAula
    suspend fun urlArchivoAula(path: String): String

    suspend fun listarEntregas(actividadId: String): List<CursoEntrega>
    suspend fun listarEntregasCurso(cursoId: String): List<CursoEntrega>
    suspend fun calificarEntrega(entregaId: String, nota: Double?, comentario: String): Unit

    suspend fun listarDocentesDelCurso(cursoId: String): List<CursoDocente>
    suspend fun asignarDocente(cursoId: String, userId: String): Unit
    suspend fun quitarDocente(cursoId: String, userId: String): Unit

    suspend fun listarCursoAlumnos(colegioId: String): List<CursoAlumno>
    suspend fun listarAlumnosDelCurso(cursoId: String): List<CursoAlumno>
    suspend fun agregarAlumnosACurso(cursoId: String, colegioId: String, alumnoIds: List<String>, agregadoPor: String? = null): Unit
    suspend fun quitarAlumnoDeCurso(cursoId: String, alumnoId: String): Unit

    suspend fun listarPersonal(): List<PersonalItem>
    suspend fun listarAlumnos(colegioId: String): List<Alumno>
}

@Singleton
class AulaRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AulaRepo {

    override suspend fun listarCursos(colegioId: String): List<Curso> {
        return try {
            val lista = supabase.from("cursos").select {
                filter {
                    eq("colegio_id", colegioId)
                }
                order("nombre", Order.ASCENDING)
            }.decodeList<Curso>()
            lista.filter { it.activo != false }
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun listarMateriales(cursoId: String): List<CursoMaterial> {
        return try {
            supabase.from("curso_materiales").select {
                filter {
                    eq("curso_id", cursoId)
                }
                order("creado_en", Order.DESCENDING)
            }.decodeList<CursoMaterial>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException(
                    "Falta aplicar la migración 013 en Supabase (supabase/migrations/013_cursos_aula.sql).",
                    e,
                    "migration013"
                )
            }
            throw mapearError(e)
        }
    }

    override suspend fun guardarMaterial(material: CursoMaterial) {
        try {
            if (!material.id.isNullOrBlank()) {
                supabase.from("curso_materiales").update(material) {
                    filter {
                        eq("id", material.id)
                    }
                }
            } else {
                supabase.from("curso_materiales").insert(material)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarMaterial(id: String, archivoPath: String?) {
        try {
            supabase.from("curso_materiales").delete {
                filter {
                    eq("id", id)
                }
            }
            if (!archivoPath.isNullOrBlank()) {
                try {
                    supabase.storage.from(AulaUtils.BUCKET_AULA).delete(archivoPath)
                } catch (_: Throwable) {
                    // El archivo huérfano no bloquea
                }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarActividades(cursoId: String): List<CursoActividad> {
        return try {
            supabase.from("curso_actividades").select {
                filter {
                    eq("curso_id", cursoId)
                }
                order("fecha_limite", Order.ASCENDING)
            }.decodeList<CursoActividad>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException(
                    "Falta aplicar la migración 013 en Supabase (supabase/migrations/013_cursos_aula.sql).",
                    e,
                    "migration013"
                )
            }
            throw mapearError(e)
        }
    }

    override suspend fun guardarActividad(actividad: CursoActividad) {
        try {
            if (!actividad.id.isNullOrBlank()) {
                supabase.from("curso_actividades").update(actividad) {
                    filter {
                        eq("id", actividad.id)
                    }
                }
            } else {
                supabase.from("curso_actividades").insert(actividad)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarActividad(id: String, archivoPath: String?) {
        try {
            supabase.from("curso_actividades").delete {
                filter {
                    eq("id", id)
                }
            }
            if (!archivoPath.isNullOrBlank()) {
                try {
                    supabase.storage.from(AulaUtils.BUCKET_AULA).delete(archivoPath)
                } catch (_: Throwable) {
                    // El archivo huérfano no bloquea
                }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun subirArchivoAula(
        colegioId: String,
        cursoId: String,
        nombre: String,
        bytes: ByteArray
    ): ArchivoAula {
        val validacion = AulaUtils.validarArchivoAula(nombre, bytes.size.toLong())
        if (validacion != null) throw ApiException(validacion)

        val path = AulaUtils.rutaArchivoAula(colegioId, cursoId, nombre)
        try {
            val bucket = supabase.storage.from(AulaUtils.BUCKET_AULA)
            bucket.upload(path, bytes) {
                upsert = false
            }
            return ArchivoAula(
                archivoPath = path,
                archivoNombre = nombre,
                archivoBytes = bytes.size.toLong()
            )
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun urlArchivoAula(path: String): String {
        if (path.isBlank()) throw ApiException("Ruta de archivo no válida.")
        return try {
            supabase.storage.from(AulaUtils.BUCKET_AULA).createSignedUrl(path, 10.minutes)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarEntregas(actividadId: String): List<CursoEntrega> {
        return try {
            supabase.from("curso_entregas").select(
                columns = Columns.raw("*, alumnos(nombre, codigo)")
            ) {
                filter {
                    eq("actividad_id", actividadId)
                }
                order("enviado_en", Order.ASCENDING)
            }.decodeList<CursoEntrega>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException(
                    "Falta aplicar la migración 014 en Supabase (supabase/migrations/014_entregas_notas.sql).",
                    e,
                    "migration014"
                )
            }
            throw mapearError(e)
        }
    }

    override suspend fun listarEntregasCurso(cursoId: String): List<CursoEntrega> {
        return try {
            supabase.from("curso_entregas").select {
                filter {
                    eq("curso_id", cursoId)
                }
            }.decodeList<CursoEntrega>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                throw ApiException(
                    "Falta aplicar la migración 014 en Supabase (supabase/migrations/014_entregas_notas.sql).",
                    e,
                    "migration014"
                )
            }
            throw mapearError(e)
        }
    }

    override suspend fun calificarEntrega(entregaId: String, nota: Double?, comentario: String) {
        try {
            val params = buildJsonObject {
                put("p_entrega", entregaId)
                if (nota != null) {
                    put("p_nota", nota)
                } else {
                    put("p_nota", JsonNull)
                }
                put("p_comentario", comentario)
            }
            supabase.postgrest.rpc("calificar_entrega", params)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarDocentesDelCurso(cursoId: String): List<CursoDocente> {
        return try {
            supabase.from("curso_docentes").select {
                filter {
                    eq("curso_id", cursoId)
                }
            }.decodeList<CursoDocente>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun asignarDocente(cursoId: String, userId: String) {
        try {
            val row = buildJsonObject {
                put("curso_id", cursoId)
                put("user_id", userId)
            }
            supabase.from("curso_docentes").insert(row)
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (!msg.contains("23505") && !msg.contains("duplicate", ignoreCase = true)) {
                throw mapearError(e)
            }
        }
    }

    override suspend fun quitarDocente(cursoId: String, userId: String) {
        try {
            supabase.from("curso_docentes").delete {
                filter {
                    eq("curso_id", cursoId)
                    eq("user_id", userId)
                }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarPersonal(): List<PersonalItem> {
        return try {
            supabase.postgrest.rpc("personal_listar").decodeList<PersonalItem>()
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override suspend fun listarAlumnos(colegioId: String): List<Alumno> {
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

    override suspend fun listarCursoAlumnos(colegioId: String): List<CursoAlumno> {
        if (colegioId.isBlank()) return emptyList()
        return try {
            supabase.from("curso_alumnos").select {
                filter { eq("colegio_id", colegioId) }
            }.decodeList<CursoAlumno>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun listarAlumnosDelCurso(cursoId: String): List<CursoAlumno> {
        if (cursoId.isBlank()) return emptyList()
        return try {
            supabase.from("curso_alumnos").select {
                filter { eq("curso_id", cursoId) }
            }.decodeList<CursoAlumno>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun agregarAlumnosACurso(
        cursoId: String,
        colegioId: String,
        alumnoIds: List<String>,
        agregadoPor: String?
    ): Unit {
        if (cursoId.isBlank() || alumnoIds.isEmpty()) return
        try {
            val rows = alumnoIds.map { alumnoId ->
                buildJsonObject {
                    put("curso_id", cursoId)
                    put("alumno_id", alumnoId)
                    put("colegio_id", colegioId)
                    if (!agregadoPor.isNullOrBlank()) {
                        put("agregado_por", agregadoPor)
                    }
                }
            }
            supabase.from("curso_alumnos").insert(rows)
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (!msg.contains("23505") && !msg.contains("duplicate", ignoreCase = true)) {
                throw mapearError(e)
            }
        }
    }

    override suspend fun quitarAlumnoDeCurso(cursoId: String, alumnoId: String): Unit {
        if (cursoId.isBlank() || alumnoId.isBlank()) return
        try {
            supabase.from("curso_alumnos").delete {
                filter {
                    eq("curso_id", cursoId)
                    eq("alumno_id", alumnoId)
                }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }
}
