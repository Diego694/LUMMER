package pe.registroacademico.nativo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.DiaCalendario
import pe.registroacademico.nativo.data.model.Horario
import pe.registroacademico.nativo.data.model.Justificacion
import java.time.Instant

class DomainUnitTests {

    private fun alumno(id: String, nombre: String = "Alumno $id", nivel: String = "Primaria", grado: String = "1er grado", estado: String = "ACTIVO"): Alumno {
        return Alumno(
            id = id,
            codigo = "C$id",
            nombre = nombre,
            nivel = nivel,
            grado = grado,
            estado = estado,
            aprobado = true
        )
    }

    private fun asistencia(alumnoId: String, fecha: String, hora: String = "07:30"): Asistencia {
        return Asistencia(
            alumnoId = alumnoId,
            fecha = fecha,
            hora = hora
        )
    }

    // --- StringUtils ---

    @Test
    fun esc_escapaHtmlYComillas() {
        val input = """<a href="x">'&'</a>"""
        val esperado = "&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;"
        assertEquals(esperado, StringUtils.esc(input))
    }

    @Test
    fun esc_toleraNull() {
        assertEquals("", StringUtils.esc(null))
    }

    @Test
    fun norm_quitaTildesYMayusculas() {
        assertEquals("aeiou nandu", StringUtils.norm("ÁÉÍóú Ñandú"))
    }

    @Test
    fun initials_extraeIniciales() {
        assertEquals("AM", StringUtils.initials("ana maría pérez"))
        assertEquals("?", StringUtils.initials(""))
        assertEquals("?", StringUtils.initials(null))
    }

    @Test
    fun pct_redondeaYProtegeDivisionPorCero() {
        assertEquals(33, StringUtils.pct(1, 3))
        assertEquals(67, StringUtils.pct(2, 3))
        assertEquals(0, StringUtils.pct(5, 0))
    }

    @Test
    fun censurarNombre_enmascaraApellidos() {
        assertEquals(
            "Lucía María Qu**** Fl****",
            StringUtils.censurarNombre(nombresParam = "Lucía María", apellidosParam = "Quispe Flores")
        )
        assertEquals(
            "Juan Carlos Pé*** Rí**",
            StringUtils.censurarNombre(nombreCompleto = "Juan Carlos Pérez Ríos")
        )
        assertEquals("Ana To****", StringUtils.censurarNombre(nombreCompleto = "Ana Torres"))
        assertEquals("Madonna", StringUtils.censurarNombre(nombreCompleto = "Madonna"))
        assertEquals("Li W* X*", StringUtils.censurarNombre(nombresParam = "Li", apellidosParam = "Wu Xi"))
        assertEquals("", StringUtils.censurarNombre())
    }

    // --- DateUtils ---

    @Test
    fun zonaLima_noDependeDeUtcNiZonaLocal() {
        // 03:00 UTC del 6-oct = 22:00 del 5-oct en Lima (UTC-5): la fecha NO debe saltar al día siguiente
        val ms1 = Instant.parse("2026-10-06T03:00:00Z").toEpochMilli()
        assertEquals("2026-10-05", DateUtils.fechaZona(ms1))
        assertEquals("22:00", DateUtils.horaZona(ms1))

        // 05:00 UTC del 6-oct = 00:00 del 6-oct en Lima
        val ms2 = Instant.parse("2026-10-06T05:00:00Z").toEpochMilli()
        assertEquals("2026-10-06", DateUtils.fechaZona(ms2))
        assertEquals("00:00", DateUtils.horaZona(ms2))
    }

    @Test
    fun sincronizarReloj_usaHoraServidor() {
        // 2026-10-05 12:30 UTC = 07:30 en Lima
        val servidorUtc = Instant.parse("2026-10-05T12:30:00Z").toEpochMilli()
        DateUtils.sincronizarReloj(servidorUtc)
        assertEquals("2026-10-05", DateUtils.todayStr())
        assertEquals("07:30", DateUtils.nowHHMM())
        // restaurar reloj
        DateUtils.sincronizarReloj(System.currentTimeMillis())
    }

    @Test
    fun addDays_cruzaMesYAnio() {
        assertEquals("2027-01-01", DateUtils.addDays("2026-12-31", 1))
        assertEquals("2026-02-28", DateUtils.addDays("2026-03-01", -1))
    }

    @Test
    fun isWeekend_verificaFinDeSemana() {
        assertTrue(DateUtils.isWeekend("2026-10-03")) // Sábado
        assertTrue(DateUtils.isWeekend("2026-10-04")) // Domingo
        assertFalse(DateUtils.isWeekend("2026-10-05")) // Lunes
    }

    @Test
    fun lastWeekdays_devuelveDiasHabilesAscendentes() {
        val dias = DateUtils.lastWeekdays(7, "2026-10-04")
        val esperados = listOf(
            "2026-09-24", "2026-09-25", "2026-09-28", "2026-09-29",
            "2026-09-30", "2026-10-01", "2026-10-02"
        )
        assertEquals(esperados, dias)
        assertTrue(dias.none { DateUtils.isWeekend(it) })
    }

    @Test
    fun clasificacionErrores_redYSesion() {
        assertTrue(DateUtils.esErrorRed(Exception("Failed to fetch")))
        assertTrue(DateUtils.esErrorRed(Exception("AbortError: signal is aborted")))
        assertFalse(DateUtils.esErrorRed(Exception("violates row-level security")))
        assertTrue(DateUtils.esErrorSesion(Exception("JWT expired")))
        assertFalse(DateUtils.esErrorSesion(Exception("otro error")))
    }

    // --- CiclosUtils ---

    @Test
    fun ciclos_sonExactamenteDelIAlVI() {
        assertEquals(listOf("I", "II", "III", "IV", "V", "VI"), CiclosUtils.CICLOS)
    }

    @Test
    fun nombreCiclo_formatoFijo() {
        assertEquals("APSTI · IV CICLO", CiclosUtils.nombreCiclo("APSTI", "IV"))
        assertEquals(
            "MECANICA ELECTRICA · III CICLO · SECCIÓN A",
            CiclosUtils.nombreCiclo("MECANICA ELECTRICA", "III", "A")
        )
    }

    @Test
    fun parsearCiclo_distingueCicloYSeccion() {
        for (c in listOf("I", "II", "III", "IV", "V", "VI")) {
            assertEquals(c, CiclosUtils.parsearCiclo("APSTI · $c CICLO").ciclo)
        }
        val parsed = CiclosUtils.parsearCiclo("APSTI · IV CICLO · SECCIÓN B")
        assertEquals("IV", parsed.ciclo)
        assertEquals("B", parsed.seccion)
        assertEquals(null, CiclosUtils.parsearCiclo("APSTI 4TO CICLO I").ciclo)
        assertEquals(null, CiclosUtils.parsearCiclo("MECANICA ELECTRICA I").ciclo)
    }

    @Test
    fun compararCiclos_ordenaNaturalmente() {
        val lista = listOf(
            "X · VI CICLO",
            "X · I CICLO · SECCIÓN B",
            "X · III CICLO",
            "X · I CICLO · SECCIÓN A",
            "OTRO NOMBRE",
            "X · II CICLO"
        )
        val ordenada = lista.sortedWith { a, b -> CiclosUtils.compararCiclos(a, b) }
        val esperada = listOf(
            "X · I CICLO · SECCIÓN A",
            "X · I CICLO · SECCIÓN B",
            "X · II CICLO",
            "X · III CICLO",
            "X · VI CICLO",
            "OTRO NOMBRE"
        )
        assertEquals(esperada, ordenada)
    }

    @Test
    fun cicloCorto_remueveCarreraCuandoCoincide() {
        assertEquals("IV CICLO", CiclosUtils.cicloCorto("APSTI · IV CICLO", "APSTI"))
        assertEquals("IV CICLO", CiclosUtils.cicloCorto("apsti · IV CICLO", "APSTI"))
        assertEquals("APSTI · IV CICLO", CiclosUtils.cicloCorto("APSTI · IV CICLO", "MECANICA"))
        assertEquals("APSTI · IV CICLO", CiclosUtils.cicloCorto("APSTI · IV CICLO", ""))
    }

    @Test
    fun etiquetaCiclo_noRepiteCarrera() {
        assertEquals("APSTI · IV CICLO", CiclosUtils.etiquetaCiclo("APSTI", "APSTI · IV CICLO"))
        assertEquals("MECANICA ELECTRICA III", CiclosUtils.etiquetaCiclo("MECANICA ELECTRICA", "MECANICA ELECTRICA III"))
        assertEquals("MECANICA ELECTRICA I", CiclosUtils.etiquetaCiclo("Mecánica Eléctrica", "MECANICA ELECTRICA I"))
        assertEquals("APSTI · III", CiclosUtils.etiquetaCiclo("APSTI", "III"))
        assertEquals("I", CiclosUtils.etiquetaCiclo("", "I"))
        assertEquals("APSTI", CiclosUtils.etiquetaCiclo("APSTI", ""))
    }

    // --- CalendarioUtils ---

    @Test
    fun feriadosPeru_calculaSemanaSantaYFijos() {
        val f2026 = CalendarioUtils.feriadosPeru(2026)
        val porFecha = f2026.associate { it.fecha to it.nombre }
        assertEquals("Fiestas Patrias", porFecha["2026-07-28"])
        assertEquals("Combate de Angamos", porFecha["2026-10-08"])
        assertEquals("Navidad", porFecha["2026-12-25"])
        assertEquals("Jueves Santo", porFecha["2026-04-02"])
        assertEquals("Viernes Santo", porFecha["2026-04-03"])
        assertEquals(16, f2026.size)

        val f2027 = CalendarioUtils.feriadosPeru(2027)
        val porFecha27 = f2027.associate { it.fecha to it.nombre }
        assertEquals("Jueves Santo", porFecha27["2027-03-25"])
        assertEquals("Viernes Santo", porFecha27["2027-03-26"])
    }

    @Test
    fun diasLectivos_ignoraFeriadosYFinesDeSemana() {
        val nl = CalendarioUtils.mapaNoLectivos(
            listOf(
                DiaCalendario(fecha = "2026-10-08", tipo = "Feriado", nombre = "Angamos"),
                DiaCalendario(fecha = "2026-10-09", tipo = "Evento", nombre = "Feria")
            )
        )
        val dias = CalendarioUtils.diasLectivos("2026-10-05", "2026-10-12", nl)
        assertEquals(listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-09", "2026-10-12"), dias)
        assertFalse(CalendarioUtils.esDiaLectivo("2026-10-08", nl))
        assertTrue(CalendarioUtils.esDiaLectivo("2026-10-09", nl))
    }

    @Test
    fun limiteDeHorario_prioridadCarrera() {
        val h = listOf(
            Horario(nivel = null, horaIngreso = "08:00", toleranciaMin = 5),
            Horario(nivel = "APSTI", horaIngreso = "07:30", toleranciaMin = 10)
        )
        assertEquals("07:40", CalendarioUtils.limiteDeHorario(h, "APSTI"))
        assertEquals("08:05", CalendarioUtils.limiteDeHorario(h, "MECANICA"))
        assertEquals("08:00", CalendarioUtils.limiteDeHorario(emptyList(), "X", "08:00"))
    }

    // --- StatsUtils ---

    @Test
    fun esTardanza_comparaHoraContraLimite() {
        assertFalse(StatsUtils.esTardanza("08:00", "08:00"))
        assertTrue(StatsUtils.esTardanza("08:01", "08:00"))
        assertFalse(StatsUtils.esTardanza("07:59:30", "08:00"))
        assertFalse(StatsUtils.esTardanza("", "08:00"))
        assertFalse(StatsUtils.esTardanza(null, "08:00"))
    }

    @Test
    fun resumenDia_ignoraInactivosYCuentaTardanzas() {
        val al = listOf(
            alumno("1"),
            alumno("2"),
            alumno("3"),
            alumno("4", estado = "INACTIVO")
        )
        val asis = listOf(
            asistencia("1", "d"),
            asistencia("2", "d", "08:20"),
            asistencia("4", "d")
        )
        val r = StatsUtils.resumenDia(al, asis, "08:00")
        assertEquals(3, r.activos)
        assertEquals(2, r.presentes)
        assertEquals(1, r.tardes)
        assertEquals(1, r.puntuales)
        assertEquals(1, r.ausentes)
        assertEquals(67, r.pct)
    }

    @Test
    fun serieDiaria_agregaPorFecha() {
        val asis = listOf(
            asistencia("1", "d1"),
            asistencia("2", "d1", "09:00"),
            asistencia("1", "d2")
        )
        val serie = StatsUtils.serieDiaria(listOf("d1", "d2"), asis, 4, "08:00")
        assertEquals(2, serie.size)
        assertEquals("d1", serie[0].fecha)
        assertEquals(2, serie[0].presentes)
        assertEquals(1, serie[0].tardes)
        assertEquals(50, serie[0].pct)
        assertEquals("d2", serie[1].fecha)
        assertEquals(1, serie[1].presentes)
        assertEquals(0, serie[1].tardes)
        assertEquals(25, serie[1].pct)
    }

    @Test
    fun porGrado_agrupaYOrdenaNaturalmente() {
        val al = listOf(
            alumno("1", grado = "10mo"),
            alumno("2", grado = "2do"),
            alumno("3", grado = "2do")
        )
        val g = StatsUtils.porGrado(al, listOf(asistencia("2", "d")))
        assertEquals(2, g.size)
        assertEquals("2do", g[0].grado)
        assertEquals(2, g[0].total)
        assertEquals(1, g[0].presentes)
        assertEquals(50, g[0].pct)
        assertEquals("10mo", g[1].grado)
        assertEquals(1, g[1].total)
        assertEquals(0, g[1].presentes)
        assertEquals(0, g[1].pct)
    }

    @Test
    fun bajaAsistencia_filtraPorUmbral() {
        val al = listOf(alumno("1"), alumno("2"), alumno("3"))
        val asis = listOf(
            asistencia("1", "d1"), asistencia("1", "d2"), asistencia("1", "d3"), asistencia("1", "d4"),
            asistencia("2", "d1"),
            asistencia("3", "d1"), asistencia("3", "d2"), asistencia("3", "d3"), asistencia("3", "d4")
        )
        val r = StatsUtils.bajaAsistencia(al, asis, 85)
        assertEquals(1, r.size)
        assertEquals("2", r[0].alumno.id)
        assertEquals(25, r[0].pct)
    }

    @Test
    fun matrizAsistencia_tardanzaCuentaComoPresente() {
        val al = listOf(alumno("1"), alumno("2"), alumno("3"))
        val dias = listOf("d1", "d2", "d3", "d4")
        val asis = listOf(
            asistencia("1", "d1"),
            asistencia("1", "d2", "08:30"),
            asistencia("2", "d1"),
            asistencia("3", "d1")
        )
        val just = listOf(Justificacion(alumnoId = "2", fecha = "d2", tipo = "Permiso"))
        val m = StatsUtils.matrizAsistencia(al, asis, just, dias, "08:00")

        assertEquals(listOf("d1", "d2"), m.dias)
        assertEquals(
            listOf("PT", "PJ", "PF"),
            m.filas.map { "${it.celdas["d1"]}${it.celdas["d2"]}" }
        )
        // Regla clave: la tardanza cuenta como presente: p++ siempre que hay hora
        val fila1 = m.filas[0]
        assertEquals(2, fila1.p)
        assertEquals(1, fila1.t)
        assertEquals(0, fila1.j)
        assertEquals(0, fila1.f)
        assertEquals(100, fila1.pct)

        // 1 día con hora tardía => p=1, t=1, pct=100
        val m2 = StatsUtils.matrizAsistencia(listOf(alumno("1")), listOf(asistencia("1", "d1", "08:30")), emptyList(), listOf("d1"), "08:00")
        assertEquals(1, m2.filas[0].p)
        assertEquals(1, m2.filas[0].t)
        assertEquals(100, m2.filas[0].pct)
    }

    @Test
    fun whatsapp_formatoPeruEInternacional() {
        assertEquals("51999888777", StatsUtils.numeroWhatsApp("999 888 777"))
        assertEquals("51999888777", StatsUtils.numeroWhatsApp("+51 999-888-777"))
        assertEquals("34600111222", StatsUtils.numeroWhatsApp("+34 600 111 222"))
        assertEquals("51999888777", StatsUtils.numeroWhatsApp("51999888777"))
        assertEquals("", StatsUtils.numeroWhatsApp(""))
        assertEquals(
            "https://wa.me/51999888777?text=Hola%20%26%20chao",
            StatsUtils.enlaceWhatsApp("999888777", "Hola & chao")
        )
    }

    @Test
    fun decidirAccion_quioscoLogica() {
        assertEquals("entrada", StatsUtils.decidirAccion(null, "07:50", 45))
        assertEquals("ya_ingreso", StatsUtils.decidirAccion("07:50", null, "08:10", 45))
        assertEquals("salida", StatsUtils.decidirAccion("07:50", null, "08:35", 45))
        assertEquals("dup_salida", StatsUtils.decidirAccion("07:50", "13:00", "13:30", 45))
    }

    @Test
    fun mensajeAviso_reemplazaVariables() {
        val res = StatsUtils.mensajeAviso(
            "{alumno} faltó el {fecha} ({otra})",
            mapOf("alumno" to "Ana", "fecha" to "05/10")
        )
        assertEquals("Ana faltó el 05/10 ({otra})", res)
    }

    @Test
    fun perteneceACurso_validaCarreraYCiclo() {
        assertTrue(StatsUtils.perteneceACurso("APSTI", "APSTI · I CICLO", "APSTI", null))
        assertTrue(StatsUtils.perteneceACurso("APSTI", "APSTI · I CICLO", "APSTI", "APSTI · I CICLO"))
        assertFalse(StatsUtils.perteneceACurso("APSTI", "APSTI · I CICLO", "APSTI", "APSTI · II CICLO"))
        assertFalse(StatsUtils.perteneceACurso("APSTI", "APSTI · I CICLO", "OTRA", null))
    }

    @Test
    fun calcularRiesgo_yFaltasRestantes() {
        val al = listOf(alumno("a"), alumno("b"), alumno("c"), alumno("z", estado = "INACTIVO"))
        val nl = CalendarioUtils.mapaNoLectivos(listOf(DiaCalendario(fecha = "2026-10-08", tipo = "Feriado", nombre = "Angamos")))
        val asis = listOf(
            asistencia("a", "2026-10-05"), asistencia("a", "2026-10-06"), asistencia("a", "2026-10-07"), asistencia("a", "2026-10-09"),
            asistencia("b", "2026-10-05"), asistencia("b", "2026-10-06")
        )
        val just = listOf(Justificacion(alumnoId = "c", fecha = "2026-10-05", tipo = "Permiso"))
        val r = RiesgoUtils.calcularRiesgo(
            alumnos = al,
            asistencias = asis,
            justificaciones = just,
            noLectivos = nl,
            desde = "2026-10-05",
            hasta = "2026-10-12",
            hoy = "2026-10-12",
            limite = 30
        )
        assertEquals(3, r.size)
        val c = r.first { it.alumno.id == "c" }
        assertEquals(3, c.faltas)
        assertEquals(1, c.justificadas)
        assertEquals(75, c.pctFaltas)
        assertEquals("critico", c.nivel)

        val b = r.first { it.alumno.id == "b" }
        assertEquals(2, b.faltas)
        assertEquals(0, b.justificadas)
        assertEquals(50, b.pctFaltas)
        assertEquals("critico", b.nivel)

        val a = r.first { it.alumno.id == "a" }
        assertEquals(0, a.faltas)
        assertEquals(0, a.justificadas)
        assertEquals(0, a.pctFaltas)
        assertEquals("ok", a.nivel)
    }
}
