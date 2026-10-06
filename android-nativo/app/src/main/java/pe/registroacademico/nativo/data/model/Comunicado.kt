package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comunicado(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("titulo") val titulo: String,
    @SerialName("mensaje") val mensaje: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("creado_en") val creadoEn: String? = null,
    @SerialName("publicado_por") val publicadoPor: String? = null
)
