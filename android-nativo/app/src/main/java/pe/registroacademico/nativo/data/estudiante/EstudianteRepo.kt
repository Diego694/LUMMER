package pe.registroacademico.nativo.data.estudiante

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.ApiException
import pe.registroacademico.nativo.domain.AulaUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

interface EstudianteRepo {
    suspend fun miAlumno(): Result<AlumnoPropio?>
    suspend fun carnet(): Result<CarnetEstudiante>
    suspend fun avisos(colegioId: String? = null): Result<List<ComunicadoEstudiante>>
    suspend fun misCursos(): Result<List<CursoEstudiante>>

    suspend fun aulaMateriales(cursoId: String): Result<List<MaterialEstudiante>>
    suspend fun aulaActividades(cursoId: String): Result<List<ActividadEstudiante>>
    suspend fun aulaMiEntrega(actividadId: String): Result<EntregaEstudiante?>
    suspend fun aulaEntregar(
        actividadId: String,
        cursoId: String,
        colegioId: String,
        texto: String,
        archivoNombre: String? = null,
        archivoBytes: ByteArray? = null
    ): Result<Unit>
    suspend fun aulaUrlArchivo(path: String): Result<String>

    suspend fun miAsistencia(): Result<List<AsistenciaEstudiante>>
    suspend fun miAsistenciaCurso(cursoId: String? = null): Result<List<AsistenciaCursoEstudiante>>
    suspend fun misNotas(cursoId: String? = null): Result<List<NotaEstudiante>>
    suspend fun marcarAsistenciaQr(token: String): Result<ResultadoQrCurso>
    suspend fun marcarIngresoQr(token: String): Result<ResultadoQrIngreso>
    suspend fun fotoUrl(fotoPath: String): Result<String?>
}

@Singleton
class EstudianteRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : EstudianteRepo {

    override suspend fun miAlumno(): Result<AlumnoPropio?> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id
            ?: throw ApiException("No autenticado")

        val alumnoRpc = runCatching {
            val carnet = supabase.postgrest.rpc("mi_registro").decodeAs<CarnetEstudiante>()
            carnet.alumno
        }.getOrNull()

        alumnoRpc ?: supabase.from("alumnos").select {
            filter {
                eq("user_id", uid)
            }
            limit(1)
        }.decodeList<AlumnoPropio>().firstOrNull()
    }

    override suspend fun carnet(): Result<CarnetEstudiante> = runCatching {
        supabase.postgrest.rpc("mi_registro").decodeAs<CarnetEstudiante>()
    }

    override suspend fun avisos(colegioId: String?): Result<List<ComunicadoEstudiante>> = runCatching {
        val cid = colegioId ?: miAlumno().getOrNull()?.colegioId
        if (!cid.isNullOrBlank()) {
            supabase.from("comunicados").select {
                filter {
                    eq("colegio_id", cid)
                }
                order("fecha", Order.DESCENDING)
            }.decodeList<ComunicadoEstudiante>()
        } else {
            supabase.from("comunicados").select {
                order("fecha", Order.DESCENDING)
            }.decodeList<ComunicadoEstudiante>()
        }
    }

    override suspend fun misCursos(): Result<List<CursoEstudiante>> = runCatching {
        val cursos = supabase.from("cursos").select {
            filter {
                eq("activo", true)
            }
            order("nombre", Order.ASCENDING)
        }.decodeList<CursoEstudiante>()
        // El docente asignado está en curso_docentes (no legible por el estudiante): la migración 024 lo expone por RPC.
        val docentes = runCatching {
            supabase.postgrest.rpc("docentes_de_mis_cursos").decodeList<DocenteDeCursoDto>()
        }.getOrDefault(emptyList()).associate { it.cursoId to it.docente }
        cursos.map { c -> if (c.docente.isNullOrBlank()) c.copy(docente = docentes[c.id]) else c }
    }

    override suspend fun aulaMateriales(cursoId: String): Result<List<MaterialEstudiante>> = runCatching {
        supabase.from("curso_materiales").select {
            filter {
                eq("curso_id", cursoId)
            }
            order("creado_en", Order.DESCENDING)
        }.decodeList<MaterialEstudiante>()
    }

    override suspend fun aulaActividades(cursoId: String): Result<List<ActividadEstudiante>> = runCatching {
        supabase.from("curso_actividades").select {
            filter {
                eq("curso_id", cursoId)
            }
            order("fecha_limite", Order.ASCENDING)
        }.decodeList<ActividadEstudiante>()
    }

    override suspend fun aulaMiEntrega(actividadId: String): Result<EntregaEstudiante?> = runCatching {
        supabase.from("curso_entregas").select {
            filter {
                eq("actividad_id", actividadId)
            }
            limit(1)
        }.decodeList<EntregaEstudiante>().firstOrNull()
    }

    override suspend fun aulaEntregar(
        actividadId: String,
        cursoId: String,
        colegioId: String,
        texto: String,
        archivoNombre: String?,
        archivoBytes: ByteArray?
    ): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: throw ApiException("No autenticado")
        var pathFinal: String? = null
        var nombreFinal: String? = null
        var bytesFinal: Long? = null

        if (archivoBytes != null && archivoNombre != null) {
            val validacion = AulaUtils.validarArchivoAula(archivoNombre, archivoBytes.size.toLong())
            if (validacion != null) throw ApiException(validacion)
            val path = AulaUtils.rutaEntrega(colegioId, cursoId, userId, archivoNombre)
            supabase.storage.from(AulaUtils.BUCKET_AULA).upload(path, archivoBytes) {
                upsert = false
            }
            pathFinal = path
            nombreFinal = archivoNombre
            bytesFinal = archivoBytes.size.toLong()
        }

        val params = buildJsonObject {
            put("p_actividad", actividadId)
            put("p_texto", texto)
            if (pathFinal != null) put("p_archivo_path", pathFinal) else put("p_archivo_path", JsonNull)
            if (nombreFinal != null) put("p_archivo_nombre", nombreFinal) else put("p_archivo_nombre", JsonNull)
            if (bytesFinal != null) put("p_archivo_bytes", bytesFinal) else put("p_archivo_bytes", JsonNull)
        }
        supabase.postgrest.rpc("entregar_actividad", params)
        Unit
    }

    override suspend fun aulaUrlArchivo(path: String): Result<String> = runCatching {
        if (path.isBlank()) throw ApiException("Ruta de archivo no válida.")
        supabase.storage.from(AulaUtils.BUCKET_AULA).createSignedUrl(path, 10.minutes)
    }

    override suspend fun miAsistencia(): Result<List<AsistenciaEstudiante>> = runCatching {
        val alumno = miAlumno().getOrThrow() ?: throw ApiException("Estudiante no registrado")
        supabase.from("asistencias").select {
            filter {
                eq("alumno_id", alumno.id)
            }
            order("fecha", Order.DESCENDING)
        }.decodeList<AsistenciaEstudiante>()
    }

    override suspend fun miAsistenciaCurso(cursoId: String?): Result<List<AsistenciaCursoEstudiante>> = runCatching {
        val alumno = miAlumno().getOrThrow() ?: throw ApiException("Estudiante no registrado")
        supabase.from("asistencias_curso").select {
            filter {
                eq("alumno_id", alumno.id)
                if (!cursoId.isNullOrBlank()) {
                    eq("curso_id", cursoId)
                }
            }
            order("fecha", Order.DESCENDING)
        }.decodeList<AsistenciaCursoEstudiante>()
    }

    override suspend fun misNotas(cursoId: String?): Result<List<NotaEstudiante>> = runCatching {
        val cursos = if (!cursoId.isNullOrBlank()) {
            misCursos().getOrThrow().filter { it.id == cursoId }
        } else {
            misCursos().getOrThrow()
        }

        val resultado = mutableListOf<NotaEstudiante>()
        for (curso in cursos) {
            val cid = curso.id ?: continue
            val actividades = aulaActividades(cid).getOrDefault(emptyList())
            for (actividad in actividades) {
                val actId = actividad.id ?: continue
                val entrega = aulaMiEntrega(actId).getOrNull()
                resultado.add(
                    NotaEstudiante(
                        actividadId = actId,
                        actividadTitulo = actividad.titulo,
                        cursoId = cid,
                        cursoNombre = curso.nombre,
                        nota = entrega?.nota,
                        puntajeMax = actividad.puntajeMax,
                        comentario = entrega?.comentario.orEmpty(),
                        calificadoEn = entrega?.calificadoEn,
                        periodo = actividad.periodo
                    )
                )
            }
        }
        resultado
    }

    override suspend fun marcarAsistenciaQr(token: String): Result<ResultadoQrCurso> = runCatching {
        val params = buildJsonObject {
            put("p_token", token)
        }
        supabase.postgrest.rpc("marcar_asistencia_qr_curso", params).decodeAs<ResultadoQrCurso>()
    }

    override suspend fun marcarIngresoQr(token: String): Result<ResultadoQrIngreso> = runCatching {
        val params = buildJsonObject {
            put("p_token", token)
        }
        val dto = supabase.postgrest.rpc("marcar_asistencia_qr_ingreso", params).decodeAs<QrIngresoRespuestaDto>()
        mapearResultadoQrIngreso(dto)
    }

    override suspend fun fotoUrl(fotoPath: String): Result<String?> = runCatching {
        if (fotoPath.isBlank()) null
        else supabase.storage.from("fotos-alumnos").createSignedUrl(fotoPath, 1.hours)
    }
}
