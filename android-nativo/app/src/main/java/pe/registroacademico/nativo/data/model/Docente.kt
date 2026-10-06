package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Docente(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("nombre") val nombre: String,
    @SerialName("profesion") val profesion: String? = null,
    @SerialName("rol") val rol: String? = null,
    @SerialName("estado") val estado: String? = "ACTIVO"
)
