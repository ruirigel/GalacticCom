package com.rmrbranco.galacticcom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class IpInfo(val ipAddress: String, val country: String)

object IpApiService {

    suspend fun getIpInfo(): IpInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://ip-api.com/json") // ip-api free tier is http usually
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2000 // 2 seconds timeout
                connection.readTimeout = 2000    // 2 seconds read timeout
                connection.connect()

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val ip = jsonObject.optString("query", "0.0.0.0")
                    val country = jsonObject.optString("country", "Unknown")
                    IpInfo(ip, country)
                } else {
                    null
                }
            } catch (e: Exception) {
                // Log silently or ignore to prevent spamming logs
                null
            }
        }
    }
}