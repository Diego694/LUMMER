package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogCliente(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("mensaje") val mensaje: String? = null,
    @SerialName("detalle") val detalle: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("agente") val agente: String? = null,
    @SerialName("app") val app: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)
