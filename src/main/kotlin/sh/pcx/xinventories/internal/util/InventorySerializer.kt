package sh.pcx.xinventories.internal.util

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Serializes and deserializes ItemStack data for storage.
 */
object InventorySerializer {

    /**
     * Serializes an ItemStack array to a Base64 string.
     */
    fun serializeItemStacks(items: Array<ItemStack?>): String {
        return try {
            ByteArrayOutputStream().use { outputStream ->
                BukkitObjectOutputStream(outputStream).use { dataOutput ->
                    dataOutput.writeInt(items.size)
                    for (item in items) {
                        dataOutput.writeObject(item)
                    }
                }
                Base64Coder.encodeLines(outputStream.toByteArray())
            }
        } catch (e: Exception) {
            Logging.error("Failed to serialize items", e)
            ""
        }
    }

    /**
     * Deserializes an ItemStack array from a Base64 string.
     */
    fun deserializeItemStacks(data: String): Array<ItemStack?> {
        if (data.isBlank()) return emptyArray()

        return try {
            val bytes = Base64Coder.decodeLines(data)
            ByteArrayInputStream(bytes).use { inputStream ->
                BukkitObjectInputStream(inputStream).use { dataInput ->
                    val size = dataInput.readInt()
                    Array(size) { dataInput.readObject() as? ItemStack }
                }
            }
        } catch (e: Exception) {
            Logging.error("Failed to deserialize items", e)
            emptyArray()
        }
    }

    /**
     * Serializes a single ItemStack to a Base64 string.
     */
    fun serializeItemStack(item: ItemStack?): String {
        if (item == null) return ""

        return try {
            ByteArrayOutputStream().use { outputStream ->
                BukkitObjectOutputStream(outputStream).use { dataOutput ->
                    dataOutput.writeObject(item)
                }
                Base64Coder.encodeLines(outputStream.toByteArray())
            }
        } catch (e: Exception) {
            Logging.error("Failed to serialize item", e)
            ""
        }
    }

    /**
     * Deserializes a single ItemStack from a Base64 string.
     */
    fun deserializeItemStack(data: String): ItemStack? {
        if (data.isBlank()) return null

        return try {
            val bytes = Base64Coder.decodeLines(data)
            ByteArrayInputStream(bytes).use { inputStream ->
                BukkitObjectInputStream(inputStream).use { dataInput ->
                    dataInput.readObject() as? ItemStack
                }
            }
        } catch (e: Exception) {
            Logging.error("Failed to deserialize item", e)
            null
        }
    }

    /**
     * Serializes an inventory map to a Base64 string.
     */
    fun serializeInventoryMap(items: Map<Int, ItemStack>): String {
        if (items.isEmpty()) return ""

        return try {
            ByteArrayOutputStream().use { outputStream ->
                BukkitObjectOutputStream(outputStream).use { dataOutput ->
                    dataOutput.writeInt(items.size)
                    for ((slot, item) in items) {
                        dataOutput.writeInt(slot)
                        dataOutput.writeObject(item)
                    }
                }
                Base64Coder.encodeLines(outputStream.toByteArray())
            }
        } catch (e: Exception) {
            Logging.error("Failed to serialize inventory map", e)
            ""
        }
    }

    /**
     * Deserializes an inventory map from a Base64 string.
     */
    fun deserializeInventoryMap(data: String): Map<Int, ItemStack> {
        if (data.isBlank()) return emptyMap()

        return try {
            val bytes = Base64Coder.decodeLines(data)
            ByteArrayInputStream(bytes).use { inputStream ->
                BukkitObjectInputStream(inputStream).use { dataInput ->
                    val size = dataInput.readInt()
                    val items = mutableMapOf<Int, ItemStack>()
                    repeat(size) {
                        val slot = dataInput.readInt()
                        val item = dataInput.readObject() as? ItemStack
                        if (item != null) {
                            items[slot] = item
                        }
                    }
                    items
                }
            }
        } catch (e: Exception) {
            Logging.error("Failed to deserialize inventory map", e)
            emptyMap()
        }
    }

    /**
     * Converts an ItemStack to a map for YAML serialization.
     */
    fun itemStackToMap(item: ItemStack?): Map<String, Any>? {
        return item?.serialize()
    }

    /**
     * Converts a map back to an ItemStack.
     */
    fun mapToItemStack(map: Map<String, Any>?): ItemStack? {
        return map?.let { ItemStack.deserialize(it) }
    }

    /**
     * Serializes inventory contents to a map for YAML storage.
     */
    fun inventoryToYamlMap(items: Map<Int, ItemStack>): Map<String, Map<String, Any>> {
        return items.mapKeys { it.key.toString() }
            .mapValues { it.value.serialize() }
    }

    /**
     * Deserializes inventory contents from a YAML map.
     */
    @Suppress("UNCHECKED_CAST")
    fun yamlMapToInventory(map: Map<String, Any>?): Map<Int, ItemStack> {
        if (map == null) return emptyMap()

        return try {
            map.mapNotNull { (key, value) ->
                val slot = key.toIntOrNull() ?: return@mapNotNull null
                val itemMap = value as? Map<String, Any> ?: return@mapNotNull null
                val item = ItemStack.deserialize(itemMap)
                slot to item
            }.toMap()
        } catch (e: Exception) {
            Logging.error("Failed to deserialize YAML inventory", e)
            emptyMap()
        }
    }
}
