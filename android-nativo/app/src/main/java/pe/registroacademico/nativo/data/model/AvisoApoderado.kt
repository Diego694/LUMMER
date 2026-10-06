package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvisoApoderado(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("alumno_id") val alumnoId: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("tipo") val tipo: String,
    @SerialName("canal") val canal: String? = "WhatsApp",
    @SerialName("enviado_por") val enviadoPor: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)
