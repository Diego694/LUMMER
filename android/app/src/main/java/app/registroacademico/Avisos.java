package app.registroacademico;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Avisos al teléfono sin servicios de Google: consulta periódicamente (cada ~15 min, Android lo agrupa) los
 * comunicados nuevos del instituto con una función pública que solo responde al token de avisos, y los muestra
 * como notificación. No guarda sesión ni contraseñas: solo la dirección del servidor, la clave pública y el token.
 */
public final class Avisos {
    static final String CANAL = "comunicados";
    private static final int JOB_ID = 4721;
    private static final String PREFS = "avisos";

    private Avisos() { }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Guarda la configuración (solo https) y programa la consulta periódica. */
    public static boolean configurar(Context c, String url, String key, String token) {
        if (url == null || !url.startsWith("https://") || key == null || token == null || token.length() != 32) return false;
        SharedPreferences p = prefs(c);
        boolean cambio = !token.equals(p.getString("token", null));
        p.edit().putString("url", url.replaceAll("/+$", "")).putString("key", key).putString("token", token).apply();
        if (cambio) p.edit().remove("desde").apply();   // otro instituto o cuenta: empezar de cero sin avisar lo antiguo
        programar(c);
        return true;
    }

    /**
     * Estudiante con registro pendiente: la app consulta (aunque esté cerrada) si el instituto ya lo aprobó y, cuando
     * ocurre, muestra «¡Tu registro fue aprobado!» y deja de consultar. Solo guarda el token privado del estudiante.
     */
    public static boolean configurarAprobacion(Context c, String url, String key, String token) {
        if (url == null || !url.startsWith("https://") || key == null || token == null || token.length() != 32) return false;
        prefs(c).edit().putString("url", url.replaceAll("/+$", "")).putString("key", key).putString("aprob_token", token).apply();
        programar(c);
        return true;
    }

    private static void programar(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, AvisosJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build());
    }

    public static void detener(Context c) {
        ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB_ID);
        prefs(c).edit().clear().apply();
    }

    /** Si ya no hay nada que vigilar (ni comunicados ni aprobación), cancela la tarea periódica. */
    private static void cancelarSiVacio(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getString("token", null) == null && p.getString("aprob_token", null) == null)
            ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB_ID);
    }

    /** Consulta si el registro del estudiante ya fue aprobado. Devuelve true si mostró la notificación. */
    static boolean consultarAprobacion(Context c) {
        SharedPreferences p = prefs(c);
        String url = p.getString("url", null), key = p.getString("key", null), token = p.getString("aprob_token", null);
        if (url == null || key == null || token == null) return false;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url + "/rest/v1/rpc/estado_solicitud").openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);
            con.setRequestProperty("apikey", key);
            con.setRequestProperty("Authorization", "Bearer " + key);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try (OutputStream out = con.getOutputStream()) { out.write(new JSONObject().put("p_token", token).toString().getBytes(StandardCharsets.UTF_8)); }
            if (con.getResponseCode() != 200) return false;
            StringBuilder sb = new StringBuilder();
            try (InputStream in = con.getInputStream()) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) > 0 && sb.length() < 5000) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            String cuerpo = sb.toString().trim();
            if (cuerpo.isEmpty() || "null".equals(cuerpo)) return false;
            if (!new JSONObject(cuerpo).optBoolean("aprobado", false)) return false;
            notificar(c, "aprobacion", "¡Tu registro fue aprobado!", "Ya puedes usar tu carnet QR para registrar tu asistencia.");
            prefs(c).edit().remove("aprob_token").apply();   // listo: ya no hay nada que vigilar
            cancelarSiVacio(c);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    public static boolean activo(Context c) { return prefs(c).getString("token", null) != null || prefs(c).getString("aprob_token", null) != null; }

    private static String ahoraIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    /**
     * Consulta y notifica. Debe llamarse fuera del hilo principal. Devuelve cuántos avisos nuevos mostró.
     * La primera vez solo marca el punto de partida (no notifica comunicados antiguos).
     */
    public static int consultar(Context c) {
        consultarAprobacion(c);   // estudiante pendiente: ¿ya lo aprobaron?
        SharedPreferences p = prefs(c);
        String url = p.getString("url", null), key = p.getString("key", null), token = p.getString("token", null);
        if (url == null || key == null || token == null) return 0;
        HttpURLConnection con = null;
        try {
            String desde = p.getString("desde", null);
            String inicio = ahoraIso();
            con = (HttpURLConnection) new URL(url + "/rest/v1/rpc/comunicados_desde").openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);
            con.setRequestProperty("apikey", key);
            con.setRequestProperty("Authorization", "Bearer " + key);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            JSONObject body = new JSONObject().put("p_token", token);
            if (desde != null) body.put("p_desde", desde);
            try (OutputStream out = con.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            if (con.getResponseCode() != 200) return 0;
            StringBuilder sb = new StringBuilder();
            try (InputStream in = con.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && sb.length() < 100000) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            JSONArray lista = new JSONArray(sb.toString());
            String max = desde;
            int mostrados = 0;
            for (int i = 0; i < lista.length(); i++) {
                JSONObject m = lista.getJSONObject(i);
                String creado = m.optString("creado_en", "");
                if (!creado.isEmpty() && (max == null || creado.compareTo(max) > 0)) max = creado;
                if (desde != null) {
                    notificar(c, m.optString("id"), m.optString("titulo", "Comunicado"), m.optString("mensaje", ""));
                    mostrados++;
                }
            }
            // Primera consulta: punto de partida = ahora (lo anterior no se notifica).
            p.edit().putString("desde", desde == null ? inicio : max).apply();
            return mostrados;
        } catch (Exception e) {
            return 0;   // sin red o servidor ocupado: se reintenta en la próxima consulta
        } finally {
            if (con != null) con.disconnect();
        }
    }

    static void notificar(Context c, String id, String titulo, String mensaje) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CANAL) == null)
            nm.createNotificationChannel(new NotificationChannel(CANAL, "Comunicados", NotificationManager.IMPORTANCE_HIGH));
        Intent abrir = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CANAL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle(titulo).setContentText(mensaje)
                .setStyle(new Notification.BigTextStyle().bigText(mensaje))
                .setContentIntent(pi).setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
        try { nm.notify(Math.abs(id.hashCode()), b.build()); } catch (SecurityException ignored) { /* sin permiso de notificaciones */ }
    }
}
