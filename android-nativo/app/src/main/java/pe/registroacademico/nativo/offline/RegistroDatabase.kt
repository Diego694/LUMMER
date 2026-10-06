package pe.registroacademico.nativo.offline

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ColaOfflineEntity::class], version = 1, exportSchema = false)
abstract class RegistroDatabase : RoomDatabase() {
    abstract fun colaOfflineDao(): ColaOfflineDao
}
