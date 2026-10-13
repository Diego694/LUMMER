package pe.registroacademico.nativo.data.docente

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.AsistenciaRepo
import pe.registroacademico.nativo.data.AulaRepo
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.AsistenciaCurso
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoActividad
import pe.registroacademico.nativo.data.model.CursoDocente
import pe.registroacademico.nativo.data.model.CursoEntrega
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.esAdmin
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SesionQrRespuesta(
    @SerialName("id") val id: String,
    @SerialName("expira_en") val expiraEn: String
)

@Serializable
data class TokenQrRespuesta(
    @SerialName("token") val token: String? = null,
    @SerialName("segundos") val segundos: Long? = null,
    @SerialName("expira_en") val expiraEn: String? = null,
    @SerialName("cerrada") val cerrada: Boolean = false
)

@Serializable
data class BitacoraItem(
    @SerialName("tipo") val tipo: String,
    @SerialName("cuando") val cuando: String,
    @SerialName("persona") val persona: String? = null,
    @SerialName("carrera") val carrera: String? = null,
    @SerialName("ciclo") val ciclo: String? = null,
    @SerialName("curso") val curso: String? = null,
    @SerialName("hora") val hora: String? = null,
    @SerialName("origen") val origen: String? = null,
    @SerialName("actor") val actor: String? = null,
    @SerialName("actor_rol") val actorRol: String? = null
)

@Serializable
data class BitacoraFiltros(
    val desde: String? = null,
    val hasta: String? = null,
    val tipo: String? = null,
    val limite: Int = 500,
    val colegio: String? = null
)

@Serializable
data class ProximaActividadInfo(
    val titulo: String,
    val fechaLimite: String
)

@Serializable
data class ResumenCursoKpi(
    val total: Int = 0,
    val presentes: Int = 0,
    val faltan: Int = 0,
    val pct: Int = 0,
    val actividades: Int = 0,
    val abiertas: Int = 0,
    val porCalificar: Int = 0,
    val proxima: ProximaActividadInfo? = null
)

@Serializable
data class AlumnoBajaAsistencia(
    val id: String,
    val nombre: String,
    val presentes: Int,
    val de: Int,
    val pct: Int
)

@Serializable
data class ResumenDocenteItem(
    val curso: Curso,
    val resumen: ResumenCursoKpi,
    val bajaAsistencia: List<AlumnoBajaAsistencia> = emptyList()
)

interface DocenteQrRepo {
    suspend fun abrirSesion(cursoId: String, minutos: Int = 20): Result<SesionQrRespuesta>
    suspend fun tokenActual(sesionId: String): Result<TokenQrRespuesta>
    suspend fun cerrarSesion(sesionId: String): Result<Unit>
    suspend fun bitacora(filtros: BitacoraFiltros = BitacoraFiltros()): Result<List<BitacoraItem>>
    suspend fun resumenDocente(colegioId: String = "", userId: String = "", esAdmin: Boolean = false): Result<List<ResumenDocenteItem>>
    suspend fun bajaAsistencia(cursoId: String, colegioId: String = ""): Result<List<AlumnoBajaAsistencia>>
    suspend fun listarCursosDocente(colegioId: String = "", userId: String = "", esAdmin: Boolean = false): Result<List<Curso>>
    suspend fun asistenciasHoyCurso(colegioId: String, cursoId: String, fecha: String = DateUtils.todayStr()): Result<List<AsistenciaCurso>>
}

@Singleton
class DocenteQrRepoImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val sesionManager: SesionManager,
    private val aulaRepo: AulaRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val alumnosRepo: AlumnosRepo
) : DocenteQrRepo {

    override suspend fun abrirSesion(cursoId: String, minutos: Int): Result<SesionQrRespuesta> = runCatching {
        val params = buildJsonObject {
            put("p_curso", cursoId)
            put("p_minutos", minutos)
        }
        supabase.postgrest.rpc("qr_curso_abrir", params).decodeAs<SesionQrRespuesta>()
    }

    override suspend fun tokenActual(sesionId: String): Result<TokenQrRespuesta> = runCatching {
        val params = buildJsonObject {
            put("p_sesion", sesionId)
        }
        supabase.postgrest.rpc("qr_curso_token", params).decodeAs<TokenQrRespuesta>()
    }

    override suspend fun cerrarSesion(sesionId: String): Result<Unit> = runCatching {
        val params = buildJsonObject {
            put("p_sesion", sesionId)
        }
        supabase.postgrest.rpc("qr_curso_cerrar", params)
        Unit
    }

    override suspend fun bitacora(filtros: BitacoraFiltros): Result<List<BitacoraItem>> = runCatching {
        val cId = resolverColegioId(filtros.colegio.orEmpty())
        val params = buildJsonObject {
            if (!filtros.desde.isNullOrBlank()) put("p_desde", filtros.desde) else put("p_desde", JsonNull)
            if (!filtros.hasta.isNullOrBlank()) put("p_hasta", filtros.hasta) else put("p_hasta", JsonNull)
            if (!filtros.tipo.isNullOrBlank()) put("p_tipo", filtros.tipo) else put("p_tipo", JsonNull)
            put("p_limite", filtros.limite)
            if (cId.isNotBlank()) put("p_colegio", cId) else put("p_colegio", JsonNull)
        }
        supabase.postgrest.rpc("bitacora_registros", params).decodeAs<List<BitacoraItem>>()
    }

    override suspend fun listarCursosDocente(
        colegioId: String,
        userId: String,
        esAdmin: Boolean
    ): Result<List<Curso>> = runCatching {
        val cId = resolverColegioId(colegioId)
        val uId = resolverUserId(userId)
        val admin = if (esAdmin) true else resolverEsAdmin()

        val todosCursos = aulaRepo.listarCursos(cId)
        if (admin) {
            todosCursos
        } else {
            val asignados = runCatching {
                supabase.from("curso_docentes").select {
                    filter { eq("user_id", uId) }
                }.decodeList<CursoDocente>().mapNotNull { it.cursoId }.toSet()
            }.getOrElse { emptySet() }
            todosCursos.filter { it.id in asignados }
        }
    }

    override suspend fun asistenciasHoyCurso(
        colegioId: String,
        cursoId: String,
        fecha: String
    ): Result<List<AsistenciaCurso>> = runCatching {
        val cId = resolverColegioId(colegioId)
        asistenciaRepo.asistenciasCursoPorFecha(cId, cursoId, fecha)
    }

    override suspend fun resumenDocente(
        colegioId: String,
        userId: String,
        esAdmin: Boolean
    ): Result<List<ResumenDocenteItem>> = runCatching {
        val cId = resolverColegioId(colegioId)
        val uId = resolverUserId(userId)
        val admin = if (esAdmin) true else resolverEsAdmin()

        val cursos = listarCursosDocente(cId, uId, admin).getOrThrow()
        if (cursos.isEmpty()) return@runCatching emptyList()

        val todosAlumnos = alumnosRepo.listar(cId).filter {
            (it.estado.isNullOrBlank() || it.estado.equals("ACTIVO", ignoreCase = true)) && it.aprobado != false
        }
        val todosManuales = runCatching { aulaRepo.listarCursoAlumnos(cId) }.getOrElse { emptyList() }
        val manualesPorCurso = todosManuales.groupBy({ it.cursoId.orEmpty() }, { it.alumnoId })
            .mapValues { it.value.toSet() }

        val hoy = DateUtils.todayStr()
        val semana = DateUtils.lastWeekdays(5, hoy)
        val ahoraMs = System.currentTimeMillis()

        cursos.map { curso ->
            val cursoId = curso.id.orEmpty()
            val manuales = manualesPorCurso[cursoId].orEmpty()
            val alumnosCurso = todosAlumnos.filter { alumno ->
                StatsUtils.perteneceACurso(
                    alumnoNivel = alumno.nivel,
                    alumnoGrado = alumno.grado,
                    cursoNivel = curso.nivel,
                    cursoGrado = curso.grado,
                    matriculadoManual = manuales.contains(alumno.id)
                )
            }.sortedBy { it.nombre }

            val asistenciasSemana = semana.map { dia ->
                runCatching {
                    asistenciaRepo.asistenciasCursoPorFecha(cId, cursoId, dia)
                }.getOrElse { emptyList() }.map { it.alumnoId }.toSet()
            }

            val actividades = runCatching {
                aulaRepo.listarActividades(cursoId)
            }.getOrElse { emptyList() }

            val entregas = runCatching {
                aulaRepo.listarEntregasCurso(cursoId)
            }.getOrElse { emptyList() }

            val marcadosHoy = asistenciasSemana.lastOrNull().orEmpty()
            val presentesHoy = alumnosCurso.count { marcadosHoy.contains(it.id) }
            val totalAlumnos = alumnosCurso.size
            val faltanHoy = totalAlumnos - presentesHoy
            val pctHoy = if (totalAlumnos > 0) Math.round((presentesHoy.toFloat() / totalAlumnos) * 100) else 0

            val abiertas = actividades.filter { act ->
                (act.publicado) && (act.fechaLimite.isNullOrBlank() || parsearMs(act.fechaLimite) >= ahoraMs)
            }
            val actIds = actividades.mapNotNull { it.id }.toSet()
            val porCalificar = entregas.count { it.actividadId in actIds && it.nota == null }

            val proxima = abiertas
                .filter { !it.fechaLimite.isNullOrBlank() }
                .minByOrNull { it.fechaLimite.orEmpty() }
                ?.let { act ->
                    ProximaActividadInfo(
                        titulo = act.titulo,
                        fechaLimite = act.fechaLimite.orEmpty()
                    )
                }

            val kpi = ResumenCursoKpi(
                total = totalAlumnos,
                presentes = presentesHoy,
                faltan = faltanHoy,
                pct = pctHoy,
                actividades = actividades.size,
                abiertas = abiertas.size,
                porCalificar = porCalificar,
                proxima = proxima
            )

            val baja = calcularBajaAsistencia(alumnosCurso, asistenciasSemana)

            ResumenDocenteItem(
                curso = curso,
                resumen = kpi,
                bajaAsistencia = baja.take(5)
            )
        }
    }

    override suspend fun bajaAsistencia(
        cursoId: String,
        colegioId: String
    ): Result<List<AlumnoBajaAsistencia>> = runCatching {
        val cId = resolverColegioId(colegioId)
        val curso = aulaRepo.listarCursos(cId).find { it.id == cursoId }
            ?: throw IllegalArgumentException("Curso no encontrado")

        val alumnos = alumnosRepo.listar(cId).filter {
            (it.estado.isNullOrBlank() || it.estado.equals("ACTIVO", ignoreCase = true)) && it.aprobado != false
        }
        val manuales = runCatching {
            aulaRepo.listarAlumnosDelCurso(cursoId).map { it.alumnoId }.toSet()
        }.getOrElse { emptySet() }

        val alumnosCurso = alumnos.filter { alumno ->
            StatsUtils.perteneceACurso(
                alumnoNivel = alumno.nivel,
                alumnoGrado = alumno.grado,
                cursoNivel = curso.nivel,
                cursoGrado = curso.grado,
                matriculadoManual = manuales.contains(alumno.id)
            )
        }

        val hoy = DateUtils.todayStr()
        val semana = DateUtils.lastWeekdays(5, hoy)
        val asistenciasSemana = semana.map { dia ->
            runCatching {
                asistenciaRepo.asistenciasCursoPorFecha(cId, cursoId, dia)
            }.getOrElse { emptyList() }.map { it.alumnoId }.toSet()
        }

        calcularBajaAsistencia(alumnosCurso, asistenciasSemana)
    }

    private fun calcularBajaAsistencia(
        alumnos: List<Alumno>,
        dias: List<Set<String>>,
        minimoPct: Int = 60
    ): List<AlumnoBajaAsistencia> {
        val clases = dias.filter { it.isNotEmpty() }
        if (clases.size < 2) return emptyList()

        return alumnos.map { alumno ->
            val presentes = clases.count { it.contains(alumno.id) }
            val pct = Math.round((presentes.toFloat() / clases.size) * 100)
            AlumnoBajaAsistencia(
                id = alumno.id,
                nombre = alumno.nombre,
                presentes = presentes,
                de = clases.size,
                pct = pct
            )
        }.filter { it.pct < minimoPct }
            .sortedWith(compareBy<AlumnoBajaAsistencia> { it.pct }.thenBy { it.nombre })
    }

    private fun parsearMs(iso: String?): Long {
        if (iso.isNullOrBlank()) return Long.MAX_VALUE
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Throwable) {
            Long.MAX_VALUE
        }
    }

    private fun resolverColegioId(colegioId: String): String {
        return if (colegioId.isNotBlank()) colegioId else sesionManager.sesion.value.colegioId
    }

    private fun resolverUserId(userId: String): String {
        return if (userId.isNotBlank()) userId else sesionManager.sesion.value.userId
    }

    private fun resolverEsAdmin(): Boolean {
        val sesion = sesionManager.sesion.value
        return sesion.esAdmin || sesion.esSuperadmin
    }
}
