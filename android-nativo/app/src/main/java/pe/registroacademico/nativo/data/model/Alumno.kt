package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Alumno(
    @SerialName("id") val id: String,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("codigo") val codigo: String,
    @SerialName("nombre") val nombre: String,
    @SerialName("nivel") val nivel: String = "",
    @SerialName("grado") val grado: String = "",
    @SerialName("apoderado") val apoderado: String = "",
    @SerialName("estado") val estado: String = "ACTIVO",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("nombres") val nombres: String? = null,
    @SerialName("apellidos") val apellidos: String? = null,
    @SerialName("dni") val dni: String? = null,
    @SerialName("foto_path") val fotoPath: String? = null,
    @SerialName("foto_data") val fotoData: String? = null,
    @SerialName("aprobado") val aprobado: Boolean? = null,
    @SerialName("consentimiento_en") val consentimientoEn: String? = null,
    @SerialName("registrado_en") val registradoEn: String? = null,
    @SerialName("apoderado_telefono") val apoderadoTelefono: String? = null,
    @SerialName("apoderado_email") val apoderadoEmail: String? = null,
    @SerialName("qr_secreto") val qrSecreto: String? = null,
    @SerialName("codigo_apoderado") val codigoApoderado: String? = null,
    @SerialName("notif_token") val notifToken: String? = null,
    @SerialName("aviso") val aviso: List<String>? = null
)
