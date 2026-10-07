package pe.registroacademico.nativo.ui.shell

import pe.registroacademico.nativo.ui.aula.pantallasAula
import pe.registroacademico.nativo.ui.consultas.pantallasConsultas
import pe.registroacademico.nativo.ui.gestion.pantallasGestion
import pe.registroacademico.nativo.ui.registro.pantallasRegistro
import pe.registroacademico.nativo.ui.sistema.pantallasSistema

object Pantallas {
    val todas: List<Pantalla> by lazy {
        val registradas = (
            pantallasRegistro +
            pantallasConsultas +
            pantallasGestion +
            pantallasAula +
            pantallasSistema
        ).associateBy { it.id }

        CatalogoPantallas.catalogo.map { def ->
            val reg = registradas[def.id]
            if (reg != null) {
                reg.copy(
                    noDocente = def.noDocente || reg.noDocente,
                    soloAdmin = def.soloAdmin || reg.soloAdmin,
                    soloSuper = def.soloSuper || reg.soloSuper
                )
            } else {
                Pantalla(
                    id = def.id,
                    titulo = def.titulo,
                    icono = def.icono,
                    grupo = def.grupo,
                    soloAdmin = def.soloAdmin,
                    soloSuper = def.soloSuper,
                    noDocente = def.noDocente,
                    contenido = { ctx ->
                        PantallaPendiente(titulo = def.titulo, ctx = ctx)
                    }
                )
            }
        }
    }
}
