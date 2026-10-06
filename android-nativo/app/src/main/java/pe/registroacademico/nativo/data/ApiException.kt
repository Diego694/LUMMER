package pe.registroacademico.nativo.data

class ApiException(
    message: String,
    cause: Throwable? = null,
    val codigo: String? = null
) : Exception(message, cause)

fun mapearError(e: Throwable): ApiException {
    if (e is ApiException) return e

    val msg = e.message ?: ""
    val isNetwork = !msg.contains("code") && (
        msg.contains("fetch", ignoreCase = true) ||
        msg.contains("network", ignoreCase = true) ||
        msg.contains("abort", ignoreCase = true) ||
        msg.contains("timeout", ignoreCase = true) ||
        msg.contains("timed out", ignoreCase = true) ||
        msg.contains("load failed", ignoreCase = true) ||
        msg.contains("conexi", ignoreCase = true) ||
        e is java.net.SocketException ||
        e is java.net.UnknownHostException ||
        e is java.io.IOException
    )

    if (isNetwork) {
        return ApiException("Error de conexión. Comprueba tu conexión a internet.", e, "network")
    }

    val isSession = msg.contains("jwt", ignoreCase = true) ||
        msg.contains("expired", ignoreCase = true) ||
        msg.contains("not authenticated", ignoreCase = true) ||
        msg.contains("no autenticado", ignoreCase = true) ||
        msg.contains("invalid token", ignoreCase = true) ||
        msg.contains("401")

    if (isSession) {
        return ApiException("Sesión expirada o no válida. Inicia sesión de nuevo.", e, "auth")
    }

    if (msg.contains("23505") || msg.contains("duplicate", ignoreCase = true)) {
        return ApiException("Ya existe un registro con esos datos (duplicado).", e, "duplicate")
    }

    if (msg.contains("42501") || msg.contains("row-level security", ignoreCase = true)) {
        return ApiException("No tienes permisos suficientes para realizar esta acción.", e, "rls")
    }

    if (msg.contains("PGRST202") || msg.contains("42883") || msg.contains("does not exist", ignoreCase = true) || msg.contains("schema cache", ignoreCase = true)) {
        return ApiException("Función o tabla no disponible en el servidor.", e, "schema")
    }

    return ApiException(if (msg.isNotBlank()) msg else "Ocurrió un error inesperado al conectar con el servidor.", e)
}

inline fun <T> ejecutarSeguro(accion: () -> T): T {
    return try {
        accion()
    } catch (e: Throwable) {
        throw mapearError(e)
    }
}
