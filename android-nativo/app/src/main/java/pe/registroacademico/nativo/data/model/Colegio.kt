package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Colegio(
    @SerialName("id")
    val id: Long,
    @SerialName("nombre")
    val nombre: String
)
