package pe.registroacademico.nativo.offline

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {

    @Provides
    @Singleton
    fun provideRegistroDatabase(@ApplicationContext context: Context): RegistroDatabase {
        return Room.databaseBuilder(
            context,
            RegistroDatabase::class.java,
            "registro_offline.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideColaOfflineDao(database: RegistroDatabase): ColaOfflineDao {
        return database.colaOfflineDao()
    }

    @Provides
    @Singleton
    fun provideColaOfflineRepository(
        dao: ColaOfflineDao,
        @ApplicationContext context: Context
    ): ColaOfflineRepository {
        return ColaOfflineRepository(dao, context)
    }
}
