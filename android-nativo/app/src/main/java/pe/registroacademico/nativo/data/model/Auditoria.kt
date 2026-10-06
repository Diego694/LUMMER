package pe.registroacademico.nativo.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

object StringOrLongSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrLong", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return try {
            decoder.decodeString()
        } catch (_: Exception) {
            decoder.decodeLong().toString()
        }
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        if (element is JsonPrimitive) return element.content
        return element.toString()
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

@Serializable
data class Auditoria(
    @Serializable(with = StringOrLongSerializer::class)
    @SerialName("id") val id: String? = null,
    @SerialName("colegio_id") val colegioId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("usuario") val usuario: String? = null,
    @SerialName("accion") val accion: String,
    @SerialName("tabla") val tabla: String,
    @SerialName("registro_id") val registroId: String? = null,
    @SerialName("detalle") val detalle: JsonElement? = null,
    @SerialName("creado_en") val creadoEn: String? = null
)
