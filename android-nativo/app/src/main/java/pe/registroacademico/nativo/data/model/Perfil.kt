package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Perfil(
    @SerialName("id") val id: String,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("rol") val rol: String? = null,
    @SerialName("carrera") val carrera: String? = null,
    @SerialName("nombre") val nombre: String? = null,
    @SerialName("foto_path") val fotoPath: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("colegios") val colegios: ColegioRelacion? = null
)

@Serializable
data class ColegioRelacion(
    @SerialName("nombre") val nombre: String? = null,
    @SerialName("qr_modo") val qrModo: String? = null
)
