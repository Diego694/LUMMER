package pe.registroacademico.nativo.domain

enum class Rol(val etiqueta: String) {
    ADMIN("Administrador"),
    COORDINADOR("Coordinador"),
    DOCENTE("Docente");

    companion object {
        fun desdeString(r: String?): Rol {
            val rol = r?.trim()?.lowercase() ?: ""
            return when {
                rol == "administrador" || rol == "admin" -> ADMIN
                rol == "coordinador" -> COORDINADOR
                else -> DOCENTE
            }
        }
    }
}

object Permisos {

    fun rolActual(rolString: String?): Rol = Rol.desdeString(rolString)

    fun esAdmin(rol: Rol): Boolean = rol == Rol.ADMIN
    fun esAdmin(rolString: String?): Boolean = Rol.desdeString(rolString) == Rol.ADMIN

    fun esSuper(esSuperadmin: Boolean): Boolean = esSuperadmin

    val ACCIONES_ADMIN = setOf(
        "nivel-new", "nivel-del", "grado-new", "grado-del", "ciclos-new", "al-new", "al-edit", "al-del", "al-import", "al-revisar",
        "sol-ver", "sol-ok", "sol-no", "sol-actualizar", "of-ver", "of-exportar", "of-importar",
        "co-del", "cd-generar", "cd-regenerar", "pers-crear", "pers-existente", "pers-pass", "pers-edit", "pers-del", "curso-new", "curso-edit", "curso-del", "aula-docentes",
        "just-del", "err-borrar", "respaldo-json", "respaldo-csv-alumnos", "respaldo-csv-asistencias", "inst-nombre-cambiar", "inst-qr-guardar"
    )

    fun puede(accion: String, rol: Rol): Boolean {
        return rol == Rol.ADMIN || accion !in ACCIONES_ADMIN
    }

    fun puede(accion: String, rolString: String?): Boolean {
        return puede(accion, Rol.desdeString(rolString))
    }

    val ETIQUETA_ROL: Map<Rol, String> = mapOf(
        Rol.ADMIN to "Administrador",
        Rol.COORDINADOR to "Coordinador",
        Rol.DOCENTE to "Docente"
    )
}
