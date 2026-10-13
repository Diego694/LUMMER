package pe.registroacademico.nativo.data.estudiante

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EstudianteModule {

    @Binds
    @Singleton
    abstract fun bindEstudianteRepo(impl: EstudianteRepoImpl): EstudianteRepo
}
