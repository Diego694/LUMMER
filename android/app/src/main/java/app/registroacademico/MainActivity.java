package app.registroacademico;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Envoltorio nativo mínimo: abre la web publicada (BuildConfig.APP_URL) en un WebView.
 * Toda la lógica vive en la web, por lo que cada actualización publicada (incluida la conexión a la base de
 * datos) llega sin reinstalar el APK. Esta clase solo cubre lo que un WebView no hace solo:
 * permiso de cámara, selector de archivos, guardar descargas, NFC y la pantalla sin conexión.
 */
public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private static final int REQ_CAMERA = 1;
    private static final int REQ_FILE = 2;
    private static final long RELOAD_AFTER_MS = 15 * 60 * 1000; // al volver tras 15 min en segundo plano, trae la última versión

    private WebView web;
    private String appHost;
    private PermissionRequest pendingPermission;
    private ValueCallback<Uri[]> fileCallback;
    private NfcAdapter nfc;
    private boolean nfcWanted = false;
    private long pausedAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appHost = Uri.parse(BuildConfig.APP_URL).getHost();
        nfc = NfcAdapter.getDefaultAdapter(this);

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(getResources().getColor(R.color.navy, getTheme()));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);            // localStorage: sesión de Supabase, tema, datos demo
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " RegistroAcademicoApp/" + BuildConfig.VERSION_NAME);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(BuildConfig.APP_URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (pausedAt != 0 && SystemClock.elapsedRealtime() - pausedAt > RELOAD_AFTER_MS) web.reload();
        pausedAt = 0;
        if (nfcWanted) enableNfc();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedAt = SystemClock.elapsedRealtime();
        disableNfc();
        web.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.removeJavascriptInterface("AndroidBridge"); web.destroy(); }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    /* ------------------------------ Navegación ------------------------------ */

    private boolean esNuestro(Uri u) {
        return u != null && appHost != null && appHost.equalsIgnoreCase(u.getHost());
    }

    private void mostrarSinConexion(String motivo) {
        try {
            web.loadUrl("file:///android_asset/offline.html?m=" + URLEncoder.encode(motivo, "UTF-8"));
        } catch (Exception e) {
            web.loadUrl("file:///android_asset/offline.html");
        }
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            if (esNuestro(u) || "file".equals(u.getScheme())) return false;
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
            return true; // enlaces externos → navegador del sistema
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame() && esNuestro(r.getUrl())) mostrarSinConexion("No hay conexión con el servidor.");
        }

        @Override
        public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse e) {
            if (r.isForMainFrame() && esNuestro(r.getUrl()) && e.getStatusCode() >= 400)
                mostrarSinConexion("El servidor respondió con el error " + e.getStatusCode() + ".");
        }
    }

    /* ------------------------ Cámara y selector de archivos ------------------------ */

    private class Chrome extends WebChromeClient {
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                if (!esNuestro(request.getOrigin())) { request.deny(); return; }
                boolean quiereCamara = false;
                for (String r : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) quiereCamara = true;
                if (!quiereCamara) { request.deny(); return; }
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    pendingPermission = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                }
            });
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (pendingPermission == request) pendingPermission = null;
        }

        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                startActivityForResult(params.createIntent(), REQ_FILE);
            } catch (Exception e) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "No se pudo abrir el selector de archivos", Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_CAMERA || pendingPermission == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        else {
            pendingPermission.deny();
            Toast.makeText(this, "Permiso de cámara denegado. Actívalo en Ajustes o usa el código manual.", Toast.LENGTH_LONG).show();
        }
        pendingPermission = null;
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_FILE || fileCallback == null) return;
        fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
        fileCallback = null;
    }

    /* ------------------------------------ NFC ------------------------------------ */

    private void enableNfc() {
        if (nfc == null || !nfc.isEnabled()) return;
        nfc.enableReaderMode(this, this,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V,
                null);
    }

    private void disableNfc() {
        if (nfc != null) try { nfc.disableReaderMode(this); } catch (Exception ignored) { }
    }

    /** Lee el primer registro de texto/URI del tag NDEF y lo entrega a la web (window.onNativeNfc). */
    @Override
    public void onTagDiscovered(Tag tag) {
        String code = null;
        try {
            Ndef ndef = Ndef.get(tag);
            NdefMessage msg = ndef != null ? ndef.getCachedNdefMessage() : null;
            if (msg != null) for (NdefRecord rec : msg.getRecords()) {
                code = decodeRecord(rec);
                if (code != null && !code.isEmpty()) break;
            }
        } catch (Exception ignored) { }
        final String result = code;
        runOnUiThread(() -> {
            if (result == null || result.isEmpty())
                Toast.makeText(this, "No se pudo leer el contenido del tag NFC", Toast.LENGTH_SHORT).show();
            else web.evaluateJavascript("window.onNativeNfc && window.onNativeNfc(" + JSONObject.quote(result) + ")", null);
        });
    }

    private static String decodeRecord(NdefRecord rec) {
        byte[] p = rec.getPayload();
        if (p == null || p.length == 0) return null;
        if (rec.getTnf() == NdefRecord.TNF_WELL_KNOWN && java.util.Arrays.equals(rec.getType(), NdefRecord.RTD_TEXT)) {
            int lang = p[0] & 0x3F;
            Charset cs = (p[0] & 0x80) == 0 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16;
            if (p.length <= 1 + lang) return null;
            return new String(p, 1 + lang, p.length - 1 - lang, cs).trim();
        }
        Uri u = rec.toUri();
        if (u != null) return u.toString().trim();
        return new String(p, StandardCharsets.UTF_8).trim();
    }

    /* ------------------------- Puente JavaScript ↔ nativo ------------------------- */

    private class Bridge {
        /** "on" | "off" | "none" */
        @JavascriptInterface
        public String nfcState() {
            if (nfc == null) return "none";
            return nfc.isEnabled() ? "on" : "off";
        }

        @JavascriptInterface
        public void startNfc() { nfcWanted = true; runOnUiThread(MainActivity.this::enableNfc); }

        @JavascriptInterface
        public void stopNfc() { nfcWanted = false; runOnUiThread(MainActivity.this::disableNfc); }

        @JavascriptInterface
        public void retry() { runOnUiThread(() -> web.loadUrl(BuildConfig.APP_URL)); }

        /** Guarda un archivo (recibido en base64) en la carpeta Descargas. Devuelve true si se guardó. */
        @JavascriptInterface
        public boolean saveFile(String name, String mime, String base64) {
            try {
                String safe = name == null ? "archivo" : name.replaceAll("[^\\w.\\- ]", "_");
                if (safe.length() > 100) safe = safe.substring(safe.length() - 100);
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                ContentResolver cr = getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                v.put(MediaStore.Downloads.MIME_TYPE, (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime.split(";")[0]);
                v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/");
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("MediaStore no devolvió URI");
                try (OutputStream out = cr.openOutputStream(uri)) { out.write(data); }
                v.clear();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                cr.update(uri, v, null, null);
                final String shown = safe;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Guardado en Descargas: " + shown, Toast.LENGTH_LONG).show());
                return true;
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "No se pudo guardar el archivo", Toast.LENGTH_LONG).show());
                return false;
            }
        }
    }
}
