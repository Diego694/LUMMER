package pe.registroacademico.nativo.data.estudiante

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlumnoPropio(
    @SerialName("id") val id: String,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("codigo") val codigo: String,
    @SerialName("nombre") val nombre: String,
    @SerialName("nivel") val nivel: String = "",
    @SerialName("grado") val grado: String = "",
    @SerialName("apoderado") val apoderado: String = "",
    @SerialName("estado") val estado: String = "ACTIVO",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("nombres") val nombres: String? = null,
    @SerialName("apellidos") val apellidos: String? = null,
    @SerialName("dni") val dni: String? = null,
    @SerialName("foto_path") val fotoPath: String? = null,
    @SerialName("foto_data") val fotoData: String? = null,
    @SerialName("aprobado") val aprobado: Boolean? = true,
    @SerialName("consentimiento_en") val consentimientoEn: String? = null,
    @SerialName("registrado_en") val registradoEn: String? = null,
    @SerialName("apoderado_telefono") val apoderadoTelefono: String? = null,
    @SerialName("apoderado_email") val apoderadoEmail: String? = null,
    @SerialName("qr_secreto") val qrSecreto: String? = null,
    @SerialName("codigo_apoderado") val codigoApoderado: String? = null,
    @SerialName("notif_token") val notifToken: String? = null
) {
    val estaAprobado: Boolean get() = aprobado != false
}

@Serializable
data class CarnetEstudiante(
    @SerialName("alumno") val alumno: AlumnoPropio,
    @SerialName("colegio") val colegio: String = "",
    @SerialName("qr_modo") val qrModo: String = "obligatorio"
)

@Serializable
data class ComunicadoEstudiante(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("titulo") val titulo: String,
    @SerialName("mensaje") val mensaje: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("creado_en") val creadoEn: String? = null,
    @SerialName("publicado_por") val publicadoPor: String? = null
)

@Serializable
data class CursoEstudiante(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("nivel") val nivel: String,
    @SerialName("grado") val grado: String? = null,
    @SerialName("nombre") val nombre: String,
    @SerialName("docente") val docente: String? = null,
    @SerialName("activo") val activo: Boolean? = true
)

@Serializable
data class DocenteDeCursoDto(
    @SerialName("curso_id") val cursoId: String,
    @SerialName("docente") val docente: String? = null
)

@Serializable
data class AsistenciaEstudiante(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("alumno_id") val alumnoId: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("hora") val hora: String,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_en") val registradoEn: String? = null,
    @SerialName("origen") val origen: String? = null,
    @SerialName("hora_salida") val horaSalida: String? = null
)

@Serializable
data class AsistenciaCursoEstudiante(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("alumno_id") val alumnoId: String,
    @SerialName("curso_id") val cursoId: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("hora") val hora: String,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_en") val registradoEn: String? = null,
    @SerialName("origen") val origen: String? = null,
    @SerialName("sesion_id") val sesionId: String? = null
)

@Serializable
data class MaterialEstudiante(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("curso_id") val cursoId: String,
    @SerialName("tema") val tema: String = "General",
    @SerialName("tipo") val tipo: String = "documento", // documento, enlace, aviso
    @SerialName("titulo") val titulo: String,
    @SerialName("descripcion") val descripcion: String = "",
    @SerialName("url") val url: String? = null,
    @SerialName("archivo_path") val archivoPath: String? = null,
    @SerialName("archivo_nombre") val archivoNombre: String? = null,
    @SerialName("archivo_bytes") val archivoBytes: Long? = null,
    @SerialName("publicado") val publicado: Boolean = true,
    @SerialName("creado_por") val creadoPor: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)

@Serializable
data class ActividadEstudiante(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("curso_id") val cursoId: String,
    @SerialName("titulo") val titulo: String,
    @SerialName("instrucciones") val instrucciones: String = "",
    @SerialName("fecha_limite") val fechaLimite: String? = null,
    @SerialName("puntaje_max") val puntajeMax: Double = 20.0,
    @SerialName("archivo_path") val archivoPath: String? = null,
    @SerialName("archivo_nombre") val archivoNombre: String? = null,
    @SerialName("archivo_bytes") val archivoBytes: Long? = null,
    @SerialName("publicado") val publicado: Boolean = true,
    @SerialName("creado_por") val creadoPor: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null,
    @SerialName("periodo") val periodo: Int = 1
)

@Serializable
data class EntregaEstudiante(
    @SerialName("id") val id: String,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("curso_id") val cursoId: String? = null,
    @SerialName("actividad_id") val actividadId: String,
    @SerialName("alumno_id") val alumnoId: String,
    @SerialName("texto") val texto: String = "",
    @SerialName("archivo_path") val archivoPath: String? = null,
    @SerialName("archivo_nombre") val archivoNombre: String? = null,
    @SerialName("archivo_bytes") val archivoBytes: Long? = null,
    @SerialName("enviado_en") val enviadoEn: String? = null,
    @SerialName("tardia") val tardia: Boolean = false,
    @SerialName("nota") val nota: Double? = null,
    @SerialName("comentario") val comentario: String = "",
    @SerialName("calificado_por") val calificadoPor: String? = null,
    @SerialName("calificado_en") val calificadoEn: String? = null
)

@Serializable
data class NotaEstudiante(
    @SerialName("actividad_id") val actividadId: String,
    @SerialName("actividad_titulo") val actividadTitulo: String,
    @SerialName("curso_id") val cursoId: String,
    @SerialName("curso_nombre") val cursoNombre: String,
    @SerialName("nota") val nota: Double? = null,
    @SerialName("puntaje_max") val puntajeMax: Double = 20.0,
    @SerialName("comentario") val comentario: String = "",
    @SerialName("calificado_en") val calificadoEn: String? = null,
    @SerialName("periodo") val periodo: Int = 1
)

@Serializable
data class ResultadoQrCurso(
    @SerialName("estado") val estado: String,
    @SerialName("mensaje") val mensaje: String = "",
    @SerialName("curso") val curso: String? = null,
    @SerialName("hora") val hora: String? = null
)

/**
 * Estados posibles devueltos por la RPC marcar_asistencia_qr_ingreso según el contrato compartido.
 */
@Serializable
enum class EstadoQrIngreso(val clave: String) {
    @SerialName("ok") OK("ok"),
    @SerialName("ya_marcado") YA_MARCADO("ya_marcado"),
    @SerialName("invalido") INVALIDO("invalido"),
    @SerialName("vencido") VENCIDO("vencido"),
    @SerialName("cerrado") CERRADO("cerrado"),
    @SerialName("no_pertenece") NO_PERTENECE("no_pertenece"),
    @SerialName("temprano") TEMPRANO("temprano"),
    @SerialName("fuera_horario") FUERA_HORARIO("fuera_horario"),
    @SerialName("pendiente") PENDIENTE("pendiente"),
    @SerialName("inactivo") INACTIVO("inactivo"),
    @SerialName("desconocido") DESCONOCIDO("desconocido");

    companion object {
        fun desdeString(raw: String?): EstadoQrIngreso {
            val normalizado = raw?.trim()?.lowercase() ?: return DESCONOCIDO
            return entries.firstOrNull { it.clave == normalizado } ?: when (normalizado) {
                "cerrado_ingreso" -> CERRADO
                else -> DESCONOCIDO
            }
        }
    }
}

/**
 * DTO para deserializar el JSON devuelto por marcar_asistencia_qr_ingreso.
 */
@Serializable
data class QrIngresoRespuestaDto(
    @SerialName("estado") val estado: String? = null,
    @SerialName("hora") val hora: String? = null,
    @SerialName("tarde") val tarde: Boolean? = null,
    @SerialName("nombre") val nombre: String? = null
)

/**
 * Jerarquía sellada que representa el resultado de marcar asistencia por QR de ingreso.
 */
sealed class ResultadoQrIngreso {
    abstract val estado: EstadoQrIngreso
    abstract val hora: String?
    abstract val tarde: Boolean
    abstract val nombre: String?

    data class Ok(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.OK
    }

    data class YaMarcado(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.YA_MARCADO
    }

    data class Invalido(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.INVALIDO
    }

    data class Vencido(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.VENCIDO
    }

    data class Cerrado(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.CERRADO
    }

    data class NoPertenece(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.NO_PERTENECE
    }

    data class Temprano(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.TEMPRANO
    }

    data class FueraHorario(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.FUERA_HORARIO
    }

    data class Pendiente(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.PENDIENTE
    }

    data class Inactivo(
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.INACTIVO
    }

    data class Desconocido(
        val rawEstado: String? = null,
        override val hora: String? = null,
        override val tarde: Boolean = false,
        override val nombre: String? = null
    ) : ResultadoQrIngreso() {
        override val estado: EstadoQrIngreso get() = EstadoQrIngreso.DESCONOCIDO
    }
}

/**
 * Función pura que mapea el DTO del JSON devuelto por Supabase al modelo sellado ResultadoQrIngreso.
 */
fun mapearResultadoQrIngreso(dto: QrIngresoRespuestaDto): ResultadoQrIngreso {
    val tarde = dto.tarde ?: false
    return when (EstadoQrIngreso.desdeString(dto.estado)) {
        EstadoQrIngreso.OK -> ResultadoQrIngreso.Ok(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.YA_MARCADO -> ResultadoQrIngreso.YaMarcado(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.INVALIDO -> ResultadoQrIngreso.Invalido(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.VENCIDO -> ResultadoQrIngreso.Vencido(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.CERRADO -> ResultadoQrIngreso.Cerrado(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.NO_PERTENECE -> ResultadoQrIngreso.NoPertenece(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.TEMPRANO -> ResultadoQrIngreso.Temprano(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.FUERA_HORARIO -> ResultadoQrIngreso.FueraHorario(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.PENDIENTE -> ResultadoQrIngreso.Pendiente(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.INACTIVO -> ResultadoQrIngreso.Inactivo(dto.hora, tarde, dto.nombre)
        EstadoQrIngreso.DESCONOCIDO -> ResultadoQrIngreso.Desconocido(dto.estado, dto.hora, tarde, dto.nombre)
    }
}

/**
 * Sobrecarga de conveniencia para mapear campos individuales a ResultadoQrIngreso.
 */
fun mapearResultadoQrIngreso(
    estado: String?,
    hora: String? = null,
    tarde: Boolean? = null,
    nombre: String? = null
): ResultadoQrIngreso = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado, hora, tarde, nombre))

/**
 * Formatea el texto principal a mostrar en la pantalla para los estados de éxito (ok, ya_marcado).
 */
fun formatearTextoExitoIngreso(resultado: ResultadoQrIngreso): String {
    val h = resultado.hora?.take(8).orEmpty()
    return when (resultado) {
        is ResultadoQrIngreso.Ok -> {
            val base = if (h.isNotBlank()) "Ingreso registrado a las $h" else "Ingreso registrado"
            if (resultado.tarde) "$base · con tardanza" else base
        }
        is ResultadoQrIngreso.YaMarcado -> {
            if (h.isNotBlank()) "Ya registraste tu ingreso a las $h" else "Ya registraste tu ingreso"
        }
        else -> ""
    }
}

/**
 * Devuelve el mensaje en español correspondiente a estados no exitosos de QR de ingreso.
 */
fun mensajeErrorIngreso(resultado: ResultadoQrIngreso): String {
    return when (resultado) {
        is ResultadoQrIngreso.Vencido -> "El código venció, escanea el que se muestra ahora"
        is ResultadoQrIngreso.Cerrado -> "El registro de ingreso está cerrado"
        is ResultadoQrIngreso.Temprano,
        is ResultadoQrIngreso.FueraHorario -> "Fuera del horario de ingreso"
        is ResultadoQrIngreso.NoPertenece -> "Este código QR no corresponde a tu colegio"
        is ResultadoQrIngreso.Invalido -> "Código QR de ingreso no válido"
        is ResultadoQrIngreso.Pendiente -> "Tu registro de estudiante está pendiente de aprobación"
        is ResultadoQrIngreso.Inactivo -> "Tu cuenta de estudiante se encuentra inactiva"
        is ResultadoQrIngreso.Desconocido -> "No se pudo registrar el ingreso"
        is ResultadoQrIngreso.Ok,
        is ResultadoQrIngreso.YaMarcado -> ""
    }
}

/**
 * Devuelve el texto descriptivo asociado a cualquier estado de ingreso.
 */
fun mensajeParaResultadoIngreso(resultado: ResultadoQrIngreso): String {
    return when (resultado) {
        is ResultadoQrIngreso.Ok,
        is ResultadoQrIngreso.YaMarcado -> formatearTextoExitoIngreso(resultado)
        else -> mensajeErrorIngreso(resultado)
    }
}
