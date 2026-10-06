package pe.registroacademico.nativo.ui.consultas.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object ConsultasExportHelper {

    fun compartirCsv(context: Context, titulo: String, nombreArchivo: String, csv: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, nombreArchivo)
            putExtra(Intent.EXTRA_TEXT, csv)
            type = "text/comma-separated-values"
        }
        val chooser = Intent.createChooser(sendIntent, titulo).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            sendIntent.type = "text/plain"
            context.startActivity(Intent.createChooser(sendIntent, titulo).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun compartirTexto(context: Context, titulo: String, texto: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, texto)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, titulo).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun abrirEnlace(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun abrirCorreo(context: Context, email: String, asunto: String, cuerpo: String): Boolean {
        return try {
            val uri = Uri.parse("mailto:${Uri.encode(email)}?subject=${Uri.encode(asunto)}&body=${Uri.encode(cuerpo)}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
