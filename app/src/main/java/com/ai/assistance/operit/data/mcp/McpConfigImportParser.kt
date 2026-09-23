package com.ai.assistance.operit.data.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Parsed standard MCP configuration used by both UI and market imports. */
internal data class McpConfigImport(
    val servers: List<McpImportedServer>
)

internal sealed interface McpImportedServer {
    val id: String
    val disabled: Boolean
}

internal data class StdioMcpImportedServer(
    override val id: String,
    val command: String,
    val args: List<String>,
    val env: Map<String, String>,
    val autoApprove: List<String>,
    override val disabled: Boolean
) : McpImportedServer

internal data class RemoteMcpImportedServer(
    override val id: String,
    val endpoint: String,
    val connectionType: String,
    val headers: Map<String, String>,
    override val disabled: Boolean
) : McpImportedServer

/**
 * Parses the public MCP configuration format without conflating HTTP transports with stdio.
 */
internal object McpConfigImportParser {
    private const val STREAMABLE_HTTP_TYPE = "streamable_http"
    private const val SSE_TYPE = "sse"
    private const val STDIO_TYPE = "stdio"

    fun parse(jsonConfig: String): McpConfigImport {
        val root = try {
            JsonParser.parseString(jsonConfig)
        } catch (e: Exception) {
            throw IllegalArgumentException("Config is not valid JSON", e)
        }

        require(root.isJsonObject) { "Config root must be a JSON object" }
        val mcpServers = root.asJsonObject.requiredObject("mcpServers")
        require(mcpServers.entrySet().isNotEmpty()) { "mcpServers must not be empty" }

        return McpConfigImport(
            servers = mcpServers.entrySet().map { (serverId, configElement) ->
                parseServer(serverId, configElement)
            }
        )
    }

    private fun parseServer(serverId: String, configElement: JsonElement): McpImportedServer {
        require(serverId.isNotBlank()) { "mcpServers contains an empty server id" }
        require(configElement.isJsonObject) { "mcpServers.$serverId must be a JSON object" }

        val config = configElement.asJsonObject
        val declaredType = config.optionalString("type")
        return if (config.has("command")) {
            parseStdioServer(serverId, config, declaredType)
        } else {
            parseRemoteServer(serverId, config, declaredType)
        }
    }

    private fun parseStdioServer(
        serverId: String,
        config: JsonObject,
        declaredType: String?
    ): StdioMcpImportedServer {
        require(declaredType == null || declaredType == STDIO_TYPE) {
            "mcpServers.$serverId declares both command and a non-stdio transport"
        }

        return StdioMcpImportedServer(
            id = serverId,
            command = config.requiredNonBlankString("command", serverId),
            args = config.optionalStringList("args", serverId),
            env = config.optionalStringMap("env", serverId),
            autoApprove = config.optionalStringList("autoApprove", serverId),
            disabled = config.optionalBoolean("disabled", serverId)
        )
    }

    private fun parseRemoteServer(
        serverId: String,
        config: JsonObject,
        declaredType: String?
    ): RemoteMcpImportedServer {
        val connectionType = when (declaredType) {
            STREAMABLE_HTTP_TYPE -> "httpStream"
            SSE_TYPE -> SSE_TYPE
            STDIO_TYPE -> throw IllegalArgumentException("mcpServers.$serverId is missing command")
            null -> throw IllegalArgumentException("mcpServers.$serverId is missing command or type")
            else -> throw IllegalArgumentException(
                "mcpServers.$serverId uses an unsupported transport: $declaredType"
            )
        }

        return RemoteMcpImportedServer(
            id = serverId,
            endpoint = config.requiredNonBlankString("url", serverId),
            connectionType = connectionType,
            headers = config.optionalStringMap("headers", serverId),
            disabled = config.optionalBoolean("disabled", serverId)
        )
    }

    private fun JsonObject.requiredObject(field: String): JsonObject {
        val value = get(field)
            ?: throw IllegalArgumentException("Config is missing the $field field")
        require(value.isJsonObject) { "$field must be a JSON object" }
        return value.asJsonObject
    }

    private fun JsonObject.requiredNonBlankString(field: String, serverId: String): String {
        val value = optionalString(field)
            ?: throw IllegalArgumentException("mcpServers.$serverId is missing $field")
        require(value.isNotBlank()) { "mcpServers.$serverId field $field must not be blank" }
        return value.trim()
    }

    private fun JsonObject.optionalString(field: String): String? {
        val value = get(field) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$field must be a string" }
        return value.asString
    }

    private fun JsonObject.optionalBoolean(field: String, serverId: String): Boolean {
        val value = get(field) ?: return false
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
            "mcpServers.$serverId field $field must be a boolean"
        }
        return value.asBoolean
    }

    private fun JsonObject.optionalStringList(field: String, serverId: String): List<String> {
        val value = get(field) ?: return emptyList()
        require(value.isJsonArray) { "mcpServers.$serverId field $field must be an array of strings" }
        return value.asJsonArray.mapIndexed { index, item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                "mcpServers.$serverId field $field[$index] must be a string"
            }
            item.asString
        }
    }

    private fun JsonObject.optionalStringMap(field: String, serverId: String): Map<String, String> {
        val value = get(field) ?: return emptyMap()
        require(value.isJsonObject) { "mcpServers.$serverId field $field must be a JSON object" }
        return value.asJsonObject.entrySet().associate { (key, item) ->
            require(key.isNotBlank()) { "mcpServers.$serverId field $field contains an empty key" }
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                "mcpServers.$serverId field $field.$key must be a string"
            }
            key to item.asString
        }
    }
}
