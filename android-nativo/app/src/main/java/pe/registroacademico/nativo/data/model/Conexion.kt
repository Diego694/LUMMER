package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConexionDatos(
    @SerialName("id") val id: String = "",
    @SerialName("nombre") val nombre: String,
    @SerialName("tipo") val tipo: String,
    @SerialName("url") val url: String,
    @SerialName("llave_publica") val llavePublica: String,
    @SerialName("estado") val estado: String = "pendiente",
    @SerialName("destino") val destino: Boolean = false,
    @SerialName("detalle") val detalle: String = "",
    @SerialName("ultima_prueba") val ultimaPrueba: String? = null,
    @SerialName("ultima_copia") val ultimaCopia: String? = null,
    @SerialName("creado_por") val creadoPor: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)

@Serializable
data class NuevaConexionPayload(
    @SerialName("nombre") val nombre: String,
    @SerialName("tipo") val tipo: String,
    @SerialName("url") val url: String,
    @SerialName("llave_publica") val llavePublica: String
)
