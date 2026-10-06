package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AsistenciaCurso(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("alumno_id") val alumnoId: String,
    @SerialName("curso_id") val cursoId: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("hora") val hora: String,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_en") val registradoEn: String? = null,
    @SerialName("origen") val origen: String? = null
)
