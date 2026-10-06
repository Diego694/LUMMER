package pe.registroacademico.nativo.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import pe.registroacademico.nativo.data.AsistenciaRepo
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.SalidaFila
import pe.registroacademico.nativo.domain.DateUtils

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun asistenciaRepo(): AsistenciaRepo
        fun colaDao(): ColaOfflineDao
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        val asistenciaRepo = entryPoint.asistenciaRepo()
        val colaDao = entryPoint.colaDao()

        val pendientes = colaDao.listarPendientes()
        if (pendientes.isEmpty()) {
            return Result.success()
        }

        var huboErrorRed = false

        // 1. Sincronizar asistencias
        val loteAsistencias = pendientes.filter { it.tipo == "asistencia" }
        for (item in loteAsistencias) {
            try {
                val asistencia = json.decodeFromString<Asistencia>(item.payloadJson)
                asistenciaRepo.registrarAsistencia(asistencia)
                colaDao.eliminarPorId(item.id)
            } catch (e: Throwable) {
                if (DateUtils.esErrorRed(e)) {
                    huboErrorRed = true
                    break
                } else {
                    val nuevosIntentos = item.intentos + 1
                    val errorMsg = (e.message ?: "Error al sincronizar").take(200)
                    colaDao.actualizar(
                        item.copy(
                            intentos = nuevosIntentos,
                            ultimoError = errorMsg,
                            rechazado = nuevosIntentos >= 3
                        )
                    )
                }
            }
        }

        // 2. Sincronizar salidas
        val loteSalidas = pendientes.filter { it.tipo == "salida" }
        for (item in loteSalidas) {
            try {
                val salida = json.decodeFromString<SalidaFila>(item.payloadJson)
                asistenciaRepo.registrarSalidas(listOf(salida))
                colaDao.eliminarPorId(item.id)
            } catch (e: Throwable) {
                if (DateUtils.esErrorRed(e)) {
                    huboErrorRed = true
                    break
                } else {
                    val nuevosIntentos = item.intentos + 1
                    val errorMsg = (e.message ?: "Error al sincronizar").take(200)
                    colaDao.actualizar(
                        item.copy(
                            intentos = nuevosIntentos,
                            ultimoError = errorMsg,
                            rechazado = nuevosIntentos >= 3
                        )
                    )
                }
            }
        }

        return if (huboErrorRed) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
