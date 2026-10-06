package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiaCalendario(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("fecha") val fecha: String,
    @SerialName("tipo") val tipo: String,
    @SerialName("nombre") val nombre: String
)
