package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CursoAlumno(
    @SerialName("curso_id") val cursoId: String,
    @SerialName("alumno_id") val alumnoId: String,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("agregado_por") val agregadoPor: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)
