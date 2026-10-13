package pe.registroacademico.nativo.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.MainActivity
import pe.registroacademico.nativo.R
import javax.inject.Inject

const val CANAL_GENERAL = "general"

/** Crea el canal de avisos (Android 8+). Se puede llamar las veces que haga falta. */
fun crearCanalAvisos(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val canal = NotificationChannel(CANAL_GENERAL, "Avisos", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Comunicados del instituto y aprobación de tu acceso"
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
}

/** Recibe los avisos de Firebase y renueva el token cuando Google lo cambia. */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {

    @Inject lateinit var registrar: PushRegistrar
    @Inject lateinit var supabase: SupabaseClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            // Sin sesión abierta solo se guarda: MainActivity lo registra al iniciar sesión
            if (supabase.auth.currentSessionOrNull() != null) registrar.registrar(token)
            else getSharedPreferences("push", MODE_PRIVATE).edit().putString("token", token).apply()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Con la app abierta FCM no muestra nada por sí solo: se dibuja aquí. Con la app cerrada lo muestra el sistema.
        val titulo = message.notification?.title ?: message.data["titulo"] ?: return
        val cuerpo = message.notification?.body ?: message.data["cuerpo"].orEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        crearCanalAvisos(this)
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CANAL_GENERAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
    }
}
