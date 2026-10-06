package pe.registroacademico.nativo.ui.shell

import pe.registroacademico.nativo.ui.consultas.pantallasConsultas
import pe.registroacademico.nativo.ui.gestion.pantallasGestion
import pe.registroacademico.nativo.ui.registro.pantallasRegistro

object Pantallas {
    val todas: List<Pantalla> by lazy {
        val registradas = (pantallasRegistro + pantallasConsultas + pantallasGestion).associateBy { it.id }

        CatalogoPantallas.catalogo.map { def ->
            registradas[def.id] ?: Pantalla(
                id = def.id,
                titulo = def.titulo,
                icono = def.icono,
                grupo = def.grupo,
                soloAdmin = def.soloAdmin,
                soloSuper = def.soloSuper,
                contenido = { ctx ->
                    PantallaPendiente(titulo = def.titulo, ctx = ctx)
                }
            )
        }
    }
}
