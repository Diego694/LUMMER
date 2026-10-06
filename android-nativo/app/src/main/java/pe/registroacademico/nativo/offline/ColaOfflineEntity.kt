package pe.registroacademico.nativo.offline

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cola_offline")
data class ColaOfflineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "tipo")
    val tipo: String,

    @ColumnInfo(name = "clave")
    val clave: String? = null,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "en")
    val en: Long,

    @ColumnInfo(name = "intentos")
    val intentos: Int = 0,

    @ColumnInfo(name = "rechazado")
    val rechazado: Boolean = false,

    @ColumnInfo(name = "ultimo_error")
    val ultimoError: String? = null
)
