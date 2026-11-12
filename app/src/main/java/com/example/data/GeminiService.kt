package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToolCallRequest(
    val id: String,
    val name: String,
    val args: Map<String, Any?>
)

sealed class GeminiResponse {
    data class TextContent(val text: String) : GeminiResponse()
    data class FunctionCall(val calls: List<ToolCallRequest>, val rawThought: String? = null) : GeminiResponse()
    data class Error(val message: String) : GeminiResponse()
}

class GeminiService(
    private val apiKey: String,
    private val modelName: String = "gemini-2.5-flash"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

    suspend fun generateWithTools(
        systemInstruction: String,
        history: List<JSONObject>
    ): GeminiResponse = withContext(Dispatchers.IO) {
        try {
            val requestBodyJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                })
                put("contents", JSONArray(history.map { it }))
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", getFunctionDeclarations())
                }))
            }

            val request = Request.Builder()
                .url("$baseUrl?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = responseBody?.let {
                    try { JSONObject(it).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
                } ?: "HTTP Error ${response.code}: ${response.message}"
                return@withContext GeminiResponse.Error(errorMsg)
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext GeminiResponse.Error("No candidates returned from Gemini API")
            }

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()

            val textParts = mutableListOf<String>()
            val functionCalls = mutableListOf<ToolCallRequest>()

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    textParts.add(part.getString("text"))
                }
                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    val name = fc.getString("name")
                    val argsObj = fc.optJSONObject("args") ?: JSONObject()
                    val argsMap = mutableMapOf<String, Any?>()
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        argsMap[k] = argsObj.get(k)
                    }
                    val callId = "call_${System.currentTimeMillis()}_$i"
                    functionCalls.add(ToolCallRequest(callId, name, argsMap))
                }
            }

            if (functionCalls.isNotEmpty()) {
                val thought = if (textParts.isNotEmpty()) textParts.joinToString("\n") else null
                return@withContext GeminiResponse.FunctionCall(functionCalls, rawThought = thought)
            } else {
                val combinedText = textParts.joinToString("\n")
                return@withContext GeminiResponse.TextContent(combinedText)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext GeminiResponse.Error("Connection Error: ${e.localizedMessage}")
        }
    }

    private fun getFunctionDeclarations(): JSONArray {
        val tools = JSONArray()

        // 1. list_directory
        tools.put(JSONObject().apply {
            put("name", "list_directory")
            put("description", "List files and subdirectories in a workspace path.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Relative folder path (empty string or '/' for root)")
                    })
                })
            })
        })

        // 2. read_file
        tools.put(JSONObject().apply {
            put("name", "read_file")
            put("description", "Read complete text content of a workspace file.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Relative file path (e.g. 'src/Main.kt')")
                    })
                })
                put("required", JSONArray().put("path"))
            })
        })

        // 3. read_lines
        tools.put(JSONObject().apply {
            put("name", "read_lines")
            put("description", "Read specific line range of a file.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Relative file path")
                    })
                    put("start", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Start line number (1-indexed)")
                    })
                    put("end", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "End line number (inclusive)")
                    })
                })
                put("required", JSONArray().put("path").put("start").put("end"))
            })
        })

        // 4. file_stat
        tools.put(JSONObject().apply {
            put("name", "file_stat")
            put("description", "Get file size, line count, sha256 hash, and MIME type without loading full file.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Relative file path")
                    })
                })
                put("required", JSONArray().put("path"))
            })
        })

        // 5. write_file
        tools.put(JSONObject().apply {
            put("name", "write_file")
            put("description", "Write or overwrite a workspace file with new content.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Relative target path")
                    })
                    put("content", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Complete new file content")
                    })
                })
                put("required", JSONArray().put("path").put("content"))
            })
        })

        // 6. edit_file
        tools.put(JSONObject().apply {
            put("name", "edit_file")
            put("description", "Propose edits to an existing workspace file.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Relative target path")
                    })
                    put("new_content", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Updated full content of the file")
                    })
                })
                put("required", JSONArray().put("path").put("new_content"))
            })
        })

        // 7. search_index
        tools.put(JSONObject().apply {
            put("name", "search_index")
            put("description", "Search project codebase fast using local SQLite FTS5 index. Returns matching relative file paths, line numbers, and snippets.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Search query (symbol, class name, or text snippet)")
                    })
                })
                put("required", JSONArray().put("query"))
            })
        })

        // 8. run_shell_command
        tools.put(JSONObject().apply {
            put("name", "run_shell_command")
            put("description", "Run shell command in workspace directory (e.g. gradle assembleDebug, git status).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Shell command to execute")
                    })
                })
                put("required", JSONArray().put("command"))
            })
        })

        // 9. git_status
        tools.put(JSONObject().apply {
            put("name", "git_status")
            put("description", "Check git status, current branch, modified files, and untracked files in the workspace.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // 10. get_device_telemetry
        tools.put(JSONObject().apply {
            put("name", "get_device_telemetry")
            put("description", "Get phone hardware telemetry: battery %, charging status, available RAM, free storage GB, network type, device model, and Android OS version.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        return tools
    }
}
