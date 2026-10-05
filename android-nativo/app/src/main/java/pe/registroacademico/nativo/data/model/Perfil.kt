package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Perfil(
    @SerialName("id")
    val id: String,
    @SerialName("nombre")
    val nombre: String? = null,
    @SerialName("rol")
    val rol: String? = null,
    @SerialName("carrera")
    val carrera: String? = null,
    @SerialName("colegio_id")
    val colegioId: Long? = null
)
