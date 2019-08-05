package org.whispersystems.signalservice.loki.api

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import okhttp3.*
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException

public object LokiGroupChatAPI {
    private val serverURL = "https://chat.lokinet.org"
    private val batchCount = 80
    @JvmStatic
    public val publicChatID: Long = 1

    @JvmStatic
    public fun getMessages(groupID: Long): Promise<List<LokiGroupMessage>, Exception> {
        Log.d("Loki", "Getting messages for group chat with ID: $groupID.")
        val queryParameters = "include_annotations=1&count=-$batchCount"
        val url = "$serverURL/channels/$groupID/messages?$queryParameters"
        val request = Request.Builder().url(url).get()
        val connection = OkHttpClient()
        val deferred = deferred<List<LokiGroupMessage>, Exception>()
        connection.newCall(request.build()).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        @Suppress("NAME_SHADOWING") val body = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val messagesAsJSON = body["data"] as List<*> // Intentionally throws so that the promise fails if this is missing
                        val messages = messagesAsJSON.mapNotNull { messageAsJSON ->
                            val x1 = messageAsJSON as? Map<*, *> ?: return@mapNotNull null
                            val x2 = x1["annotations"] as? List<*> ?: return@mapNotNull null
                            val x3 = x2.firstOrNull() as? Map<*, *> ?: return@mapNotNull null
                            val x4 = x3["value"] as? Map<*, *> ?: return@mapNotNull null
                            val id = x4["id"] as? String ?: x1["id"] as? String ?: return@mapNotNull null
                            val hexEncodedPublicKey = x4["source"] as? String ?: return@mapNotNull null
                            val displayName = x4["from"] as? String ?: return@mapNotNull null
                            @Suppress("NAME_SHADOWING") val body = x1["text"] as? String ?: return@mapNotNull null
                            val timestamp = x4["timestamp"] as? Long ?: return@mapNotNull null
                            LokiGroupMessage(id, hexEncodedPublicKey, displayName, body, timestamp)
                        }
                        deferred.resolve(messages)
                    }
                    else -> {
                        Log.d("Loki", "Couldn't reach group chat server.")
                        deferred.reject(LokiAPI.Error.Generic)
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                deferred.reject(exception)
            }
        })
        return deferred.promise
    }

    @JvmStatic
    public fun sendMessage(message: LokiGroupMessage, groupID: Long): Promise<Unit, Exception> {
        Log.d("Loki", "Sending message to group chat with ID: $groupID.")
        val url = "$serverURL/channels/$groupID/messages"
        val parameters = message.toJSON()
        val body = RequestBody.create(MediaType.get("application/json"), parameters)
        val request = Request.Builder().url(url).header("Authorization", "Bearer loki").post(body)
        val connection = OkHttpClient()
        val deferred = deferred<Unit, Exception>()
        connection.newCall(request.build()).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> deferred.resolve(Unit)
                    else -> {
                        Log.d("Loki", "Couldn't reach group chat server.")
                        deferred.reject(LokiAPI.Error.Generic)
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                deferred.reject(exception)
            }
        })
        return deferred.promise
    }
}