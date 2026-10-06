package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Periodo(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("nombre") val nombre: String,
    @SerialName("inicio") val inicio: String,
    @SerialName("fin") val fin: String? = null,
    @SerialName("activo") val activo: Boolean? = true,
    @SerialName("cerrado_en") val cerradoEn: String? = null,
    @SerialName("resumen") val resumen: JsonObject? = null
)
