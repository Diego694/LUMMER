package pe.registroacademico.nativo.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPerfilRepository(impl: PerfilRepositoryImpl): PerfilRepository

    @Binds
    @Singleton
    abstract fun bindAlumnosRepo(impl: AlumnosRepoImpl): AlumnosRepo

    @Binds
    @Singleton
    abstract fun bindAsistenciaRepo(impl: AsistenciaRepoImpl): AsistenciaRepo

    @Binds
    @Singleton
    abstract fun bindCatalogosRepo(impl: CatalogosRepoImpl): CatalogosRepo

    @Binds
    @Singleton
    abstract fun bindPersonalRepo(impl: PersonalRepoImpl): PersonalRepo

    @Binds
    @Singleton
    abstract fun bindInstitucionesRepo(impl: InstitucionesRepoImpl): InstitucionesRepo

    @Binds
    @Singleton
    abstract fun bindSistemaRepo(impl: SistemaRepoImpl): SistemaRepo
}
