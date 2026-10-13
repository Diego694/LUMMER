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
