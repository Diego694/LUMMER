package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Colegio(
    @SerialName("id") val id: String,
    @SerialName("nombre") val nombre: String,
    @SerialName("codigo_registro") val codigoRegistro: String? = null,
    @SerialName("qr_modo") val qrModo: String? = "off",
    @SerialName("activo") val activo: Boolean? = true,
    @SerialName("aviso_token") val avisoToken: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("desvinculado_en") val desvinculadoEn: String? = null
)
