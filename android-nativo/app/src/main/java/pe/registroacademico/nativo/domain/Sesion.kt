package pe.registroacademico.nativo.domain

import kotlinx.coroutines.flow.StateFlow
import pe.registroacademico.nativo.data.model.Colegio
import pe.registroacademico.nativo.data.model.Perfil

data class SesionEstado(
    val userId: String = "",
    val userEmail: String = "",
    val perfil: Perfil? = null,
    val rol: Rol = Rol.DOCENTE,
    val esSuperadmin: Boolean = false,
    val colegio: Colegio? = null,
    val colegioId: String = "",
    val nombreInstituto: String = "LUMMER",
    val qrModo: String = "off",
    val carrera: String? = null,
    val nombreUsuario: String = "",
    val estaCargada: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

val SesionEstado.esAdmin: Boolean get() = this.rol == Rol.ADMIN
val SesionEstado.esSuper: Boolean get() = this.esSuperadmin

interface SesionManager {
    val sesion: StateFlow<SesionEstado>
    suspend fun cargarSesion(): Result<SesionEstado>
    suspend fun refrescarInstituto(nuevoNombre: String)
    suspend fun refrescarQrModo(nuevoQrModo: String)
    fun limpiarSesion()
}
