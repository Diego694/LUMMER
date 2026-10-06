package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Grado(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("nivel") val nivel: String,
    @SerialName("nombre") val nombre: String
)
