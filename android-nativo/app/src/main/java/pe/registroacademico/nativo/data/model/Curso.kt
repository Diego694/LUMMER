package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Curso(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("nivel") val nivel: String,
    @SerialName("grado") val grado: String? = null,
    @SerialName("nombre") val nombre: String,
    @SerialName("docente") val docente: String? = null,
    @SerialName("activo") val activo: Boolean? = true
)
