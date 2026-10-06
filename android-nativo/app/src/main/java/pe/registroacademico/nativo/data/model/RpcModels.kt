package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SalidaFila(
    @SerialName("alumno_id") val alumnoId: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("hora") val hora: String
)

@Serializable
data class SalidasResultado(
    @SerialName("ok") val ok: Int = 0,
    @SerialName("dup") val dup: Int = 0,
    @SerialName("sin_entrada") val sinEntrada: Int = 0
)

@Serializable
data class PersonalItem(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String? = null,
    @SerialName("nombre") val nombre: String? = null,
    @SerialName("rol") val rol: String? = null,
    @SerialName("carrera") val carrera: String? = null,
    @SerialName("foto_path") val fotoPath: String? = null
)

@Serializable
data class PersonalDirectorioItem(
    @SerialName("id") val id: String,
    @SerialName("nombre") val nombre: String? = null,
    @SerialName("rol") val rol: String? = null,
    @SerialName("carrera") val carrera: String? = null,
    @SerialName("foto_path") val fotoPath: String? = null
)

@Serializable
data class InstitucionItem(
    @SerialName("id") val id: String,
    @SerialName("nombre") val nombre: String,
    @SerialName("codigo_registro") val codigoRegistro: String? = null,
    @SerialName("activo") val activo: Boolean? = true,
    @SerialName("creado_en") val creadoEn: String? = null,
    @SerialName("alumnos") val alumnos: Long? = 0,
    @SerialName("personal") val personal: Long? = 0,
    @SerialName("actual") val actual: Boolean? = false
)

@Serializable
data class PerfilDetalle(
    val colegioId: String,
    val rol: String,
    val nombre: String?,
    val carrera: String?,
    val fotoPath: String?,
    val colegio: String,
    val superadmin: Boolean,
    val qrModo: String
)
