package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Justificacion(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("alumno_id") val alumnoId: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("tipo") val tipo: String,
    @SerialName("motivo") val motivo: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)
