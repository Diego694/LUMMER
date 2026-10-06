package pe.registroacademico.nativo.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pe.registroacademico.nativo.data.AuthRepository
import pe.registroacademico.nativo.data.PerfilRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SesionManagerImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val perfilRepository: PerfilRepository
) : SesionManager {

    private val _sesion = MutableStateFlow(SesionEstado())
    override val sesion: StateFlow<SesionEstado> = _sesion.asStateFlow()

    override suspend fun cargarSesion(): Result<SesionEstado> {
        _sesion.update { it.copy(isLoading = true, error = null) }
        return runCatching {
            val user = authRepository.currentUser
                ?: throw IllegalStateException("No hay un usuario autenticado.")

            val userId = user.id
            val userEmail = user.email.orEmpty()

            val detalleResult = perfilRepository.getPerfilDetalle(userId)
            val detalle = detalleResult.getOrThrow()

            val rol = Rol.desdeString(detalle.rol)
            val colegio = if (detalle.colegioId.isNotBlank()) {
                perfilRepository.getColegio(detalle.colegioId).getOrNull()
            } else null

            val estado = SesionEstado(
                userId = userId,
                userEmail = userEmail,
                perfil = perfilRepository.getPerfil(userId).getOrNull(),
                rol = rol,
                esSuperadmin = detalle.superadmin,
                colegio = colegio,
                colegioId = detalle.colegioId,
                nombreInstituto = detalle.colegio.ifBlank { colegio?.nombre ?: "Lummer" },
                qrModo = detalle.qrModo,
                carrera = detalle.carrera,
                nombreUsuario = detalle.nombre ?: userEmail,
                estaCargada = true,
                isLoading = false,
                error = null
            )

            _sesion.value = estado
            estado
        }.onFailure { e ->
            _sesion.update { it.copy(isLoading = false, error = e.localizedMessage) }
        }
    }

    override suspend fun refrescarInstituto(nuevoNombre: String) {
        _sesion.update {
            it.copy(
                nombreInstituto = nuevoNombre,
                colegio = it.colegio?.copy(nombre = nuevoNombre)
            )
        }
    }

    override suspend fun refrescarQrModo(nuevoQrModo: String) {
        _sesion.update {
            it.copy(
                qrModo = nuevoQrModo,
                colegio = it.colegio?.copy(qrModo = nuevoQrModo)
            )
        }
    }

    override fun limpiarSesion() {
        _sesion.value = SesionEstado()
    }
}
