package sh.pcx.xinventories.internal.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.pcx.xinventories.internal.model.SyncMessage
import sh.pcx.xinventories.internal.util.Logging

/**
 * Handles serialization and deserialization of sync messages to/from JSON.
 * Uses kotlinx.serialization for type-safe JSON handling.
 */
object MessageSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /**
     * Serializes a SyncMessage to JSON string.
     *
     * @param message The message to serialize
     * @return JSON string representation of the message
     */
    fun serialize(message: SyncMessage): String {
        return try {
            json.encodeToString(message)
        } catch (e: Exception) {
            Logging.error("Failed to serialize sync message: ${message::class.simpleName}", e)
            throw e
        }
    }

    /**
     * Deserializes a JSON string to a SyncMessage.
     *
     * @param jsonString The JSON string to deserialize
     * @return The deserialized SyncMessage, or null if deserialization fails
     */
    fun deserialize(jsonString: String): SyncMessage? {
        return try {
            json.decodeFromString<SyncMessage>(jsonString)
        } catch (e: Exception) {
            Logging.error("Failed to deserialize sync message: $jsonString", e)
            null
        }
    }

    /**
     * Deserializes a JSON string to a specific SyncMessage type.
     *
     * @param jsonString The JSON string to deserialize
     * @return The deserialized message, or null if deserialization fails or type doesn't match
     */
    inline fun <reified T : SyncMessage> deserializeAs(jsonString: String): T? {
        val message = deserialize(jsonString)
        return message as? T
    }

    /**
     * Checks if a JSON string represents a valid SyncMessage.
     */
    fun isValid(jsonString: String): Boolean {
        return deserialize(jsonString) != null
    }
}
