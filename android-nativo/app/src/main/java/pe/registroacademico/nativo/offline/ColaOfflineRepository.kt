package pe.registroacademico.nativo.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.SalidaFila
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColaOfflineRepository @Inject constructor(
    private val dao: ColaOfflineDao,
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun contarPendientes(): Flow<Int> = dao.contarPendientesFlow()

    fun contarRechazados(): Flow<Int> = dao.contarRechazadosFlow()

    fun obtenerPendientes(): Flow<List<ColaOfflineEntity>> = dao.obtenerPendientesFlow()

    fun obtenerTodos(): Flow<List<ColaOfflineEntity>> = dao.listarTodosFlow()

    suspend fun totalPendientes(): Int = dao.contarPendientes()

    suspend fun totalRechazados(): Int = dao.contarRechazados()

    suspend fun listarTodos(): List<ColaOfflineEntity> = dao.listarTodos()

    suspend fun eliminarPorId(id: String) = dao.eliminarPorId(id)

    suspend fun vaciarRechazados() = dao.vaciarRechazados()

    suspend fun vaciarTodo() = dao.vaciarTodo()

    suspend fun encolarAsistencia(asistencia: Asistencia): Boolean {
        val clave = "a|${asistencia.alumnoId}|${asistencia.fecha}"
        val yaExiste = dao.buscarPorClave(clave)
        if (yaExiste != null) {
            return false
        }
        val entity = ColaOfflineEntity(
            id = UUID.randomUUID().toString(),
            tipo = "asistencia",
            clave = clave,
            payloadJson = json.encodeToString(asistencia),
            en = System.currentTimeMillis()
        )
        dao.insertar(entity)
        programarSincronizacion()
        return true
    }

    suspend fun encolarSalida(salida: SalidaFila): Boolean {
        val clave = "s|${salida.alumnoId}|${salida.fecha}"
        val yaExiste = dao.buscarPorClave(clave)
        if (yaExiste != null) {
            return false
        }
        val entity = ColaOfflineEntity(
            id = UUID.randomUUID().toString(),
            tipo = "salida",
            clave = clave,
            payloadJson = json.encodeToString(salida),
            en = System.currentTimeMillis()
        )
        dao.insertar(entity)
        programarSincronizacion()
        return true
    }

    fun programarSincronizacion() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("offline_sync")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "cola_offline_sync",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun forzarSincronizacion() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag("offline_sync_now")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "cola_offline_sync_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
