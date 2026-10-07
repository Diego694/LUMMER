package pe.registroacademico.nativo.ui.sistema

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import pe.registroacademico.nativo.BuildConfig
import pe.registroacademico.nativo.data.ConexionesRepo
import pe.registroacademico.nativo.data.model.ConexionDatos
import pe.registroacademico.nativo.domain.BasePropiaStore
import pe.registroacademico.nativo.domain.Conectores
import pe.registroacademico.nativo.domain.ConectoresHttp
import pe.registroacademico.nativo.domain.InformeTabla
import pe.registroacademico.nativo.domain.PaqueteLummer
import pe.registroacademico.nativo.domain.TABLAS_CONEXION
import java.time.Instant
import javax.inject.Inject

data class ConexionesUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val conexiones: List<ConexionDatos> = emptyList(),
    val operacionEnProgreso: Boolean = false,
    val copiando: Boolean = false,
    val progresoCopia: String? = null,
    val informeCopia: List<InformeTabla>? = null,
    val avisosPaquete: List<String> = emptyList(),
    val copiaTerminadaConFallo: Boolean = false,
    val baseActivaUrl: String = "",
    val esBasePropia: Boolean = false
)

@HiltViewModel
class ConexionesViewModel @Inject constructor(
    private val conexionesRepo: ConexionesRepo,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConexionesUiState())
    val uiState: StateFlow<ConexionesUiState> = _uiState.asStateFlow()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun cargar() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            val (activaUrl, esPropia) = BasePropiaStore.getBaseActiva(context, BuildConfig.SUPABASE_URL)
            try {
                val lista = conexionesRepo.listar()
                _uiState.update {
                    it.copy(
                        cargando = false,
                        conexiones = lista,
                        baseActivaUrl = activaUrl,
                        esBasePropia = esPropia
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "Error al cargar conexiones",
                        baseActivaUrl = activaUrl,
                        esBasePropia = esPropia
                    )
                }
            }
        }
    }

    fun agregar(
        nombre: String,
        tipo: String,
        url: String,
        llave: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val clasif = Conectores.clasificarLlave(llave)
        if (!clasif.ok) {
            onError(clasif.motivo ?: "Llave no válida")
            return
        }
        val norm = Conectores.normalizarDestino(tipo, url)
        if (!norm.ok) {
            onError(norm.motivo ?: "URL no válida")
            return
        }
        if (nombre.trim().length < 2) {
            onError("Escribe un nombre.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(operacionEnProgreso = true) }
            try {
                conexionesRepo.agregar(
                    nombre = nombre.trim(),
                    tipo = tipo.trim(),
                    url = norm.valor,
                    llavePublica = llave.trim()
                )
                cargar()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo guardar la conexión")
            } finally {
                _uiState.update { it.copy(operacionEnProgreso = false) }
            }
        }
    }

    fun probar(
        conexion: ConexionDatos,
        onResultado: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(operacionEnProgreso = true) }
            try {
                val r = ConectoresHttp.probarConexion(conexion.tipo, conexion.url, conexion.llavePublica)
                val nuevoEstado = if (r.ok) "verificada" else "error"
                val detalle = r.detalle.take(200)
                val fecha = Instant.now().toString()
                conexionesRepo.actualizar(
                    id = conexion.id,
                    cambios = mapOf(
                        "estado" to nuevoEstado,
                        "detalle" to detalle,
                        "ultima_prueba" to fecha
                    )
                )
                cargar()
                onResultado(r.ok, r.detalle)
            } catch (e: Exception) {
                onResultado(false, e.message ?: "Error de conexión")
            } finally {
                _uiState.update { it.copy(operacionEnProgreso = false) }
            }
        }
    }

    fun toggleDestino(
        id: String,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                conexionesRepo.marcarDestino(id, _uiState.value.conexiones)
                cargar()
            } catch (e: Exception) {
                onError(e.message ?: "Error al actualizar destino")
            }
        }
    }

    fun desasociar(
        id: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(operacionEnProgreso = true) }
            try {
                conexionesRepo.eliminar(id)
                cargar()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo desasociar")
            } finally {
                _uiState.update { it.copy(operacionEnProgreso = false) }
            }
        }
    }

    fun copiarTodosLosDatos(
        conexion: ConexionDatos,
        onError: (String) -> Unit
    ) {
        if (!conexion.destino) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    copiando = true,
                    progresoCopia = "Iniciando copia de seguridad...",
                    informeCopia = null,
                    avisosPaquete = emptyList(),
                    copiaTerminadaConFallo = false
                )
            }

            try {
                val tablas = mutableMapOf<String, List<JsonObject>>()
                val avisos = mutableListOf<String>()

                for (t in TABLAS_CONEXION) {
                    _uiState.update { it.copy(progresoCopia = "Leyendo $t…") }
                    try {
                        tablas[t] = conexionesRepo.leerTabla(t)
                    } catch (e: Exception) {
                        tablas[t] = emptyList()
                        avisos.add("$t: no se pudo leer (${e.message ?: "error"})")
                    }
                }

                val conteos = tablas.mapValues { it.value.size }
                val tablasConDatos = TABLAS_CONEXION.filter { (tablas[it]?.size ?: 0) > 0 }
                val informe = mutableListOf<InformeTabla>()
                var hecho = 0
                var falloOcurrido: String? = null

                for (t in tablasConDatos) {
                    val filas = tablas[t].orEmpty()
                    var enviadas = 0
                    var errorTabla: String? = null
                    _uiState.update {
                        it.copy(progresoCopia = "Copiando $t (${filas.size})… ($hecho/${tablasConDatos.size})")
                    }

                    val grupos = Conectores.lotes(filas, 400)
                    for (grupo in grupos) {
                        val resultado = ConectoresHttp.enviarLote(
                            tipo = conexion.tipo,
                            url = conexion.url,
                            llave = conexion.llavePublica,
                            tabla = t,
                            grupo = grupo,
                            enviadasOffset = enviadas
                        )
                        if (resultado.isFailure) {
                            errorTabla = resultado.exceptionOrNull()?.message ?: "Error al enviar datos"
                            break
                        }
                        enviadas += grupo.size
                    }

                    val destConteo = if (errorTabla == null) {
                        ConectoresHttp.contarEnDestino(conexion.tipo, conexion.url, conexion.llavePublica, t)
                    } else null

                    informe.add(
                        InformeTabla(
                            tabla = t,
                            enviadas = enviadas,
                            error = errorTabla,
                            conteoDestino = destConteo,
                            esperadas = conteos[t] ?: 0
                        )
                    )
                    hecho++

                    if (errorTabla != null) {
                        falloOcurrido = errorTabla
                        break
                    }
                }

                val huboFallo = falloOcurrido != null
                _uiState.update {
                    it.copy(
                        copiando = false,
                        progresoCopia = if (huboFallo) "Copia detenida por error" else "Copia terminada",
                        informeCopia = informe,
                        avisosPaquete = avisos,
                        copiaTerminadaConFallo = huboFallo
                    )
                }

                val ahora = Instant.now().toString()
                if (huboFallo) {
                    conexionesRepo.actualizar(
                        id = conexion.id,
                        cambios = mapOf(
                            "estado" to "error",
                            "detalle" to falloOcurrido.take(200)
                        )
                    )
                } else {
                    conexionesRepo.actualizar(
                        id = conexion.id,
                        cambios = mapOf(
                            "estado" to "copiada",
                            "detalle" to "",
                            "ultima_copia" to ahora
                        )
                    )
                }
                cargar()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        copiando = false,
                        progresoCopia = "Error inesperado durante la copia",
                        copiaTerminadaConFallo = true
                    )
                }
                onError(e.message ?: "Error al realizar la copia")
            }
        }
    }

    fun descargarPaqueteJson(
        onSuccess: (String, Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(operacionEnProgreso = true) }
            try {
                val tablas = mutableMapOf<String, List<JsonObject>>()
                val avisos = mutableListOf<String>()

                for (t in TABLAS_CONEXION) {
                    try {
                        tablas[t] = conexionesRepo.leerTabla(t)
                    } catch (e: Exception) {
                        tablas[t] = emptyList()
                        avisos.add("$t: no se pudo leer (${e.message ?: "error"})")
                    }
                }

                val conteos = tablas.mapValues { it.value.size }
                val totalFilas = conteos.values.sum()
                val paquete = PaqueteLummer(
                    formato = "lummer-paquete",
                    version = 1,
                    creado = Instant.now().toString(),
                    tablas = tablas,
                    conteos = conteos,
                    avisos = avisos
                )
                val jsonString = json.encodeToString(PaqueteLummer.serializer(), paquete)
                onSuccess(jsonString, totalFilas)
            } catch (e: Exception) {
                onError(e.message ?: "Error al crear el paquete de datos")
            } finally {
                _uiState.update { it.copy(operacionEnProgreso = false) }
            }
        }
    }
}
