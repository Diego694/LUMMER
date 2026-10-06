package pe.registroacademico.nativo.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ColaOfflineDao {
    @Query("SELECT * FROM cola_offline WHERE rechazado = 0 ORDER BY en ASC")
    fun obtenerPendientesFlow(): Flow<List<ColaOfflineEntity>>

    @Query("SELECT * FROM cola_offline WHERE rechazado = 0 ORDER BY en ASC")
    suspend fun listarPendientes(): List<ColaOfflineEntity>

    @Query("SELECT COUNT(*) FROM cola_offline WHERE rechazado = 0")
    fun contarPendientesFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cola_offline WHERE rechazado = 0")
    suspend fun contarPendientes(): Int

    @Query("SELECT * FROM cola_offline WHERE clave = :clave AND rechazado = 0 LIMIT 1")
    suspend fun buscarPorClave(clave: String): ColaOfflineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(entity: ColaOfflineEntity): Long

    @Update
    suspend fun actualizar(entity: ColaOfflineEntity)

    @Query("DELETE FROM cola_offline WHERE id = :id")
    suspend fun eliminarPorId(id: String)

    @Query("DELETE FROM cola_offline WHERE id IN (:ids)")
    suspend fun eliminarPorIds(ids: List<String>)

    @Query("SELECT * FROM cola_offline WHERE rechazado = 1 ORDER BY en DESC")
    fun listarRechazadosFlow(): Flow<List<ColaOfflineEntity>>

    @Query("SELECT COUNT(*) FROM cola_offline WHERE rechazado = 1")
    fun contarRechazadosFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cola_offline WHERE rechazado = 1")
    suspend fun contarRechazados(): Int

    @Query("DELETE FROM cola_offline WHERE rechazado = 1")
    suspend fun vaciarRechazados()

    @Query("SELECT * FROM cola_offline ORDER BY en DESC")
    fun listarTodosFlow(): Flow<List<ColaOfflineEntity>>

    @Query("SELECT * FROM cola_offline ORDER BY en DESC")
    suspend fun listarTodos(): List<ColaOfflineEntity>

    @Query("DELETE FROM cola_offline")
    suspend fun vaciarTodo()
}
