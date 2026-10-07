package pe.registroacademico.nativo.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import pe.registroacademico.nativo.BuildConfig
import pe.registroacademico.nativo.domain.BasePropiaStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context
    ): SupabaseClient {
        val override = BasePropiaStore.getOverride(context)
        val finalUrl = override?.first ?: BuildConfig.SUPABASE_URL
        val finalKey = override?.second ?: BuildConfig.SUPABASE_ANON_KEY

        return createSupabaseClient(
            supabaseUrl = finalUrl,
            supabaseKey = finalKey
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
