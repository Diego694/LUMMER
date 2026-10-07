package pe.registroacademico.nativo.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AulaModule {

    @Binds
    @Singleton
    abstract fun bindAulaRepo(impl: AulaRepoImpl): AulaRepo
}
