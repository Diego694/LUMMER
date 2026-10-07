package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.model.AvisoApoderado
import pe.registroacademico.nativo.data.model.Comunicado
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoAlumno
import pe.registroacademico.nativo.data.model.DiaCalendario
import pe.registroacademico.nativo.data.model.Docente
import pe.registroacademico.nativo.data.model.Grado
import pe.registroacademico.nativo.data.model.Horario
import pe.registroacademico.nativo.data.model.Justificacion
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.data.model.Periodo
import javax.inject.Inject
import javax.inject.Singleton

interface CatalogosRepo {
    suspend fun listarNiveles(colegioId: String): List<Nivel>
    suspend fun guardarNivel(nivel: Nivel): Unit
    suspend fun eliminarNivel(id: String): Unit

    suspend fun listarGrados(colegioId: String): List<Grado>
    suspend fun guardarGrado(grado: Grado): Unit
    suspend fun eliminarGrado(id: String): Unit

    suspend fun listarCursos(colegioId: String): List<Curso>
    suspend fun guardarCurso(curso: Curso): Unit
    suspend fun eliminarCurso(id: String): Unit

    suspend fun listarHorarios(colegioId: String): List<Horario>
    suspend fun guardarHorario(horario: Horario): Unit
    suspend fun eliminarHorario(id: String): Unit

    suspend fun listarDiasCalendario(colegioId: String): List<DiaCalendario>
    suspend fun guardarDiaCalendario(dia: DiaCalendario): Unit
    suspend fun eliminarDiaCalendario(id: String): Unit

    suspend fun listarPeriodos(colegioId: String): List<Periodo>
    suspend fun guardarPeriodo(periodo: Periodo): Unit
    suspend fun eliminarPeriodo(id: String): Unit

    suspend fun listarComunicados(colegioId: String): List<Comunicado>
    suspend fun guardarComunicado(comunicado: Comunicado): Unit
    suspend fun eliminarComunicado(id: String): Unit

    suspend fun justificacionesRango(colegioId: String, desde: String, hasta: String): List<Justificacion>
    suspend fun guardarJustificacion(justificacion: Justificacion): Unit
    suspend fun eliminarJustificacion(id: String): Unit

    suspend fun avisosPorFecha(colegioId: String, fecha: String): List<AvisoApoderado>
    suspend fun registrarAviso(aviso: AvisoApoderado): Unit
    suspend fun tokenAvisos(): String?

    suspend fun listarDocentes(colegioId: String): List<Docente>
    suspend fun cursosAsignadosADocente(userId: String): List<String>

    suspend fun listarCursoAlumnos(colegioId: String): List<CursoAlumno>
    suspend fun listarAlumnosDelCurso(cursoId: String): List<CursoAlumno>
    suspend fun agregarAlumnosACurso(cursoId: String, colegioId: String, alumnoIds: List<String>, agregadoPor: String? = null): Unit
    suspend fun quitarAlumnoDeCurso(cursoId: String, alumnoId: String): Unit
}

@Singleton
class CatalogosRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : CatalogosRepo {

    override suspend fun listarNiveles(colegioId: String): List<Nivel> {
        return try {
            supabase.from("niveles").select {
                filter { eq("colegio_id", colegioId) }
                order("nombre", Order.ASCENDING)
            }.decodeList<Nivel>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun guardarNivel(nivel: Nivel): Unit {
        try {
            if (!nivel.id.isNullOrBlank()) {
                supabase.from("niveles").update(nivel) {
                    filter { eq("id", nivel.id) }
                }
            } else {
                supabase.from("niveles").insert(nivel)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarNivel(id: String): Unit {
        try {
            supabase.from("niveles").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarGrados(colegioId: String): List<Grado> {
        return try {
            supabase.from("grados").select {
                filter { eq("colegio_id", colegioId) }
                order("nombre", Order.ASCENDING)
            }.decodeList<Grado>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun guardarGrado(grado: Grado): Unit {
        try {
            if (!grado.id.isNullOrBlank()) {
                supabase.from("grados").update(grado) {
                    filter { eq("id", grado.id) }
                }
            } else {
                supabase.from("grados").insert(grado)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarGrado(id: String): Unit {
        try {
            supabase.from("grados").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarCursos(colegioId: String): List<Curso> {
        return try {
            supabase.from("cursos").select {
                filter { eq("colegio_id", colegioId) }
                order("nombre", Order.ASCENDING)
            }.decodeList<Curso>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun guardarCurso(curso: Curso): Unit {
        try {
            if (!curso.id.isNullOrBlank()) {
                supabase.from("cursos").update(curso) {
                    filter { eq("id", curso.id) }
                }
            } else {
                supabase.from("cursos").insert(curso)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarCurso(id: String): Unit {
        try {
            supabase.from("cursos").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarHorarios(colegioId: String): List<Horario> {
        return try {
            supabase.from("horarios").select {
                filter { eq("colegio_id", colegioId) }
                order("nivel", Order.ASCENDING)
            }.decodeList<Horario>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun guardarHorario(horario: Horario): Unit {
        try {
            if (!horario.id.isNullOrBlank()) {
                supabase.from("horarios").update(horario) {
                    filter { eq("id", horario.id) }
                }
            } else {
                supabase.from("horarios").insert(horario)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarHorario(id: String): Unit {
        try {
            supabase.from("horarios").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarDiasCalendario(colegioId: String): List<DiaCalendario> {
        return try {
            supabase.from("calendario").select {
                filter { eq("colegio_id", colegioId) }
                order("fecha", Order.ASCENDING)
            }.decodeList<DiaCalendario>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun guardarDiaCalendario(dia: DiaCalendario): Unit {
        try {
            if (!dia.id.isNullOrBlank()) {
                supabase.from("calendario").update(dia) {
                    filter { eq("id", dia.id) }
                }
            } else {
                supabase.from("calendario").insert(dia)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarDiaCalendario(id: String): Unit {
        try {
            supabase.from("calendario").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarPeriodos(colegioId: String): List<Periodo> {
        return try {
            supabase.from("periodos").select {
                filter { eq("colegio_id", colegioId) }
                order("inicio", Order.ASCENDING)
            }.decodeList<Periodo>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun guardarPeriodo(periodo: Periodo): Unit {
        try {
            if (!periodo.id.isNullOrBlank()) {
                supabase.from("periodos").update(periodo) {
                    filter { eq("id", periodo.id) }
                }
            } else {
                supabase.from("periodos").insert(periodo)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarPeriodo(id: String): Unit {
        try {
            supabase.from("periodos").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun listarComunicados(colegioId: String): List<Comunicado> {
        return try {
            supabase.from("comunicados").select {
                filter { eq("colegio_id", colegioId) }
                order("fecha", Order.DESCENDING)
            }.decodeList<Comunicado>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun guardarComunicado(comunicado: Comunicado): Unit {
        try {
            if (!comunicado.id.isNullOrBlank()) {
                supabase.from("comunicados").update(comunicado) {
                    filter { eq("id", comunicado.id) }
                }
            } else {
                supabase.from("comunicados").insert(comunicado)
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarComunicado(id: String): Unit {
        try {
            supabase.from("comunicados").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun justificacionesRango(
        colegioId: String,
        desde: String,
        hasta: String
    ): List<Justificacion> {
        return try {
            supabase.from("justificaciones").select {
                filter {
                    eq("colegio_id", colegioId)
                    gte("fecha", desde)
                    lte("fecha", hasta)
                }
                order("id", Order.ASCENDING)
            }.decodeList<Justificacion>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun guardarJustificacion(justificacion: Justificacion): Unit {
        try {
            supabase.from("justificaciones").insert(justificacion)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun eliminarJustificacion(id: String): Unit {
        try {
            supabase.from("justificaciones").delete {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun avisosPorFecha(colegioId: String, fecha: String): List<AvisoApoderado> {
        return try {
            supabase.from("avisos_apoderados").select {
                filter {
                    eq("colegio_id", colegioId)
                    eq("fecha", fecha)
                }
                order("id", Order.ASCENDING)
            }.decodeList<AvisoApoderado>()
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
                emptyList()
            } else {
                throw mapearError(e)
            }
        }
    }

    override suspend fun registrarAviso(aviso: AvisoApoderado): Unit {
        try {
            supabase.from("avisos_apoderados").insert(aviso)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun tokenAvisos(): String? {
        return try {
            supabase.postgrest.rpc("token_avisos").decodeAs<String?>()
        } catch (e: Throwable) {
            null
        }
    }

    override suspend fun listarDocentes(colegioId: String): List<Docente> {
        return try {
            supabase.from("docentes").select {
                filter { eq("colegio_id", colegioId) }
                order("nombre", Order.ASCENDING)
            }.decodeList<Docente>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    @Serializable
    private data class CursoDocenteRow(
        @SerialName("curso_id") val cursoId: String
    )

    override suspend fun cursosAsignadosADocente(userId: String): List<String> {
        if (userId.isBlank()) return emptyList()
        return try {
            supabase.from("curso_docentes").select {
                filter { eq("user_id", userId) }
            }.decodeList<CursoDocenteRow>().map { it.cursoId }
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
