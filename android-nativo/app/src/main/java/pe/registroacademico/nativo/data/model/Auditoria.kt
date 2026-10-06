package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Auditoria(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("usuario") val usuario: String? = null,
    @SerialName("accion") val accion: String,
    @SerialName("tabla") val tabla: String,
    @SerialName("registro_id") val registroId: String? = null,
    @SerialName("detalle") val detalle: JsonObject? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)
