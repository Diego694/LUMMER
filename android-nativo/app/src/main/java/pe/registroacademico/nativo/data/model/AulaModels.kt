package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CursoMaterial(
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
data class CursoActividad(
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
data class AlumnoEntregaResumen(
    @SerialName("nombre") val nombre: String? = null,
    @SerialName("codigo") val codigo: String? = null
)

@Serializable
data class CursoEntrega(
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
    @SerialName("calificado_en") val calificadoEn: String? = null,
    @SerialName("alumnos") val alumnos: AlumnoEntregaResumen? = null
)

@Serializable
data class CursoDocente(
    @SerialName("curso_id") val cursoId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)

data class FilaLibroNotas(
    val alumno: Alumno,
    val notas: Map<String, Double?>,
    val promedio: Double?
)

data class ArchivoAula(
    val archivoPath: String,
    val archivoNombre: String,
    val archivoBytes: Long
)
