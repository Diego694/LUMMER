package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>
    val currentSession: UserSession?
    val currentUser: UserInfo?
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AuthRepository {

    override val sessionStatus: StateFlow<SessionStatus>
        get() = supabase.auth.sessionStatus

    override val currentSession: UserSession?
        get() = supabase.auth.currentSessionOrNull()

    override val currentUser: UserInfo?
        get() = supabase.auth.currentUserOrNull()

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return runCatching {
            supabase.auth.signOut()
        }
    }
}
