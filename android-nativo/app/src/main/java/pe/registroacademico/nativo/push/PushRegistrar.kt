package pe.registroacademico.nativo.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Registra el token de Firebase Cloud Messaging de este teléfono en Supabase (funciones registrar_dispositivo_push /
 * quitar_dispositivo_push, migración 021) para que el servidor pueda enviarle avisos.
 *
 * Sin google-services.json (compilación sin Firebase) [disponible] es false y todo queda inactivo, sin errores.
 */
@Singleton
class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient
) {
    private val prefs get() = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    fun disponible(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** Pide el token actual a Firebase y lo registra a nombre de la sesión abierta. Silencioso si falla (se reintenta al abrir). */
    suspend fun sincronizar() {
        if (!disponible()) return
        val token = obtenerToken() ?: return
        registrar(token)
    }

    /** Guarda el token (por si aún no hay sesión) y lo registra; también lo llama onNewToken. */
    suspend fun registrar(token: String) {
        prefs.edit().putString("token", token).apply()
        runCatching {
            supabase.postgrest.rpc("registrar_dispositivo_push", buildJsonObject {
                put("p_token", token)
                put("p_app", BuildConfig.PERFIL)
            })
        }
    }

    /** Se llama antes de cerrar sesión: este teléfono deja de recibir los avisos de esa cuenta. */
    suspend fun quitar() {
        val token = prefs.getString("token", null) ?: return
        runCatching {
            supabase.postgrest.rpc("quitar_dispositivo_push", buildJsonObject { put("p_token", token) })
        }
    }

    private suspend fun obtenerToken(): String? = suspendCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { t -> cont.resume(if (t.isSuccessful) t.result else null) }
        }.onFailure { cont.resume(null) }
    }
}
