package pe.registroacademico.nativo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Horario(
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("nivel") val nivel: String? = null,
    @SerialName("hora_ingreso") val horaIngreso: String,
    @SerialName("tolerancia_min") val toleranciaMin: Int? = 0,
    @SerialName("hora_salida") val horaSalida: String? = null,
    @SerialName("ingreso_desde") val ingresoDesde: String? = null,
    @SerialName("ingreso_hasta") val ingresoHasta: String? = null,
    @SerialName("permanencia_min") val permanenciaMin: Int? = 120
)
