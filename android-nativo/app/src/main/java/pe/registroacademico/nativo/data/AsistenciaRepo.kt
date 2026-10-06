package pe.registroacademico.nativo.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.AsistenciaCurso
import pe.registroacademico.nativo.data.model.SalidaFila
import pe.registroacademico.nativo.data.model.SalidasResultado
import javax.inject.Inject
import javax.inject.Singleton

interface AsistenciaRepo {
    suspend fun asistenciasPorFecha(colegioId: String, fecha: String): List<Asistencia>
    suspend fun asistenciasRango(colegioId: String, desde: String, hasta: String): List<Asistencia>
    suspend fun asistenciasAlumno(alumnoId: String, limite: Long = 100): List<Asistencia>
    suspend fun registrarAsistencia(asistencia: Asistencia): Unit
    suspend fun registrarMasivo(rows: List<Asistencia>): Int
    suspend fun registrarSalidas(rows: List<SalidaFila>): SalidasResultado
    suspend fun asistenciasCursoPorFecha(colegioId: String, cursoId: String, fecha: String): List<AsistenciaCurso>
    suspend fun registrarAsistenciaCurso(asistenciaCurso: AsistenciaCurso): Unit
    suspend fun registrarMasivoCurso(rows: List<AsistenciaCurso>): Int
}

@Singleton
class AsistenciaRepoImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AsistenciaRepo {

    override suspend fun asistenciasPorFecha(colegioId: String, fecha: String): List<Asistencia> {
        return asistenciasRango(colegioId, fecha, fecha)
    }

    override suspend fun asistenciasRango(colegioId: String, desde: String, hasta: String): List<Asistencia> {
        return try {
            supabase.from("asistencias").select {
                filter {
                    eq("colegio_id", colegioId)
                    gte("fecha", desde)
                    lte("fecha", hasta)
                }
                order("id", Order.ASCENDING)
            }.decodeList<Asistencia>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun asistenciasAlumno(alumnoId: String, limite: Long): List<Asistencia> {
        return try {
            supabase.from("asistencias").select {
                filter {
                    eq("alumno_id", alumnoId)
                }
                order("fecha", Order.DESCENDING)
                limit(limite)
            }.decodeList<Asistencia>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun registrarAsistencia(asistencia: Asistencia): Unit {
        try {
            supabase.from("asistencias").insert(asistencia)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun registrarMasivo(rows: List<Asistencia>): Int {
        if (rows.isEmpty()) return 0
        return try {
            val res = supabase.from("asistencias").upsert(rows) {
                onConflict = "alumno_id,fecha"
                ignoreDuplicates = true
            }.decodeList<JsonObject>()
            res.size
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun registrarSalidas(rows: List<SalidaFila>): SalidasResultado {
        if (rows.isEmpty()) return SalidasResultado()
        return try {
            val params = buildJsonObject {
                put("p_rows", Json.encodeToJsonElement(rows))
            }
            supabase.postgrest.rpc("registrar_salidas", params).decodeAs<SalidasResultado>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun asistenciasCursoPorFecha(
        colegioId: String,
        cursoId: String,
        fecha: String
    ): List<AsistenciaCurso> {
        return try {
            supabase.from("asistencias_curso").select {
                filter {
                    eq("colegio_id", colegioId)
                    eq("curso_id", cursoId)
                    eq("fecha", fecha)
                }
                order("id", Order.ASCENDING)
            }.decodeList<AsistenciaCurso>()
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun registrarAsistenciaCurso(asistenciaCurso: AsistenciaCurso): Unit {
        try {
            supabase.from("asistencias_curso").insert(asistenciaCurso)
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }

    override suspend fun registrarMasivoCurso(rows: List<AsistenciaCurso>): Int {
        if (rows.isEmpty()) return 0
        return try {
            val res = supabase.from("asistencias_curso").upsert(rows) {
                onConflict = "alumno_id,curso_id,fecha"
                ignoreDuplicates = true
            }.decodeList<JsonObject>()
            res.size
        } catch (e: Throwable) {
            throw mapearError(e)
        }
    }
}
