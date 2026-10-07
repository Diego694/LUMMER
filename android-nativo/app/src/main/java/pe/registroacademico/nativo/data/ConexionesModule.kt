package pe.registroacademico.nativo.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConexionesModule {

    @Binds
    @Singleton
    abstract fun bindConexionesRepo(impl: ConexionesRepoImpl): ConexionesRepo
}
