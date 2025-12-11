package com.fyrbyadditive.zapsdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for interacting with the ZAP Firmware API
 */
class ZAPClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://zap.fyrbyadditive.com"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // MARK: - Products

    /**
     * Fetches all available products
     * @return List of products
     */
    suspend fun getProducts(): List<ZAPProduct> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/products".toHttpUrl()
        val responseBody = performRequest(url)

        // API returns {"success": true, "data": [...]}
        try {
            val response = json.decodeFromString<APIResponse<List<ZAPProduct>>>(responseBody)
            response.data ?: throw DecodingException("No data in response")
        } catch (e: ZAPException) {
            throw e
        } catch (e: Exception) {
            throw DecodingException("Failed to decode products response", e)
        }
    }

    // MARK: - Firmware

    /**
     * Fetches the latest firmware for a product
     * @param product Product slug
     * @param channel Release channel (defaults to "stable")
     * @param board Optional board type filter
     * @return Latest firmware version
     */
    suspend fun getLatestFirmware(
        product: String,
        channel: String = "stable",
        board: String? = null
    ): ZAPFirmware = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/api/v1/firmware".toHttpUrl().newBuilder()
            .addQueryParameter("product", product)
            .addQueryParameter("channel", channel)

        board?.let { urlBuilder.addQueryParameter("board", it) }

        val responseBody = performRequest(urlBuilder.build())

        // API returns {"success": true, "data": {"product": ..., "channel": ..., "firmware": ...}}
        try {
            val response = json.decodeFromString<APIResponse<FirmwareResponseData>>(responseBody)
            response.data?.firmware ?: throw NotFoundException("No firmware found for product '$product'")
        } catch (e: ZAPException) {
            throw e
        } catch (e: Exception) {
            throw DecodingException("Failed to decode firmware response", e)
        }
    }

    /**
     * Fetches firmware version history for a product
     * @param product Product slug
     * @param channel Release channel (defaults to "stable")
     * @param board Optional board type filter
     * @return List of firmware versions, newest first
     */
    suspend fun getFirmwareHistory(
        product: String,
        channel: String = "stable",
        board: String? = null
    ): List<ZAPFirmware> = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/api/v1/firmware".toHttpUrl().newBuilder()
            .addQueryParameter("product", product)
            .addQueryParameter("history", "1")
            .addQueryParameter("channel", channel)

        board?.let { urlBuilder.addQueryParameter("board", it) }

        val responseBody = performRequest(urlBuilder.build())

        // API returns {"success": true, "data": {"product": ..., "channel": ..., "versions": [...]}}
        try {
            val response = json.decodeFromString<APIResponse<FirmwareHistoryResponseData>>(responseBody)
            response.data?.versions ?: emptyList()
        } catch (e: ZAPException) {
            throw e
        } catch (e: Exception) {
            throw DecodingException("Failed to decode firmware history response", e)
        }
    }

    /**
     * Fetches a specific firmware version
     * @param product Product slug
     * @param version Version string (e.g., "1.2.0")
     * @param board Optional board type filter
     * @return Firmware version details
     */
    suspend fun getFirmware(
        product: String,
        version: String,
        board: String? = null
    ): ZAPFirmware = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/api/v1/firmware".toHttpUrl().newBuilder()
            .addQueryParameter("product", product)
            .addQueryParameter("version", version)

        board?.let { urlBuilder.addQueryParameter("board", it) }

        val responseBody = performRequest(urlBuilder.build())

        // API returns {"success": true, "data": {"product": ..., "channel": ..., "firmware": ...}}
        try {
            val response = json.decodeFromString<APIResponse<FirmwareResponseData>>(responseBody)
            response.data?.firmware ?: throw NotFoundException("Firmware version '$version' not found")
        } catch (e: ZAPException) {
            throw e
        } catch (e: Exception) {
            throw DecodingException("Failed to decode firmware response", e)
        }
    }

    // MARK: - Downloads

    /**
     * Downloads firmware binary
     * @param product Product slug
     * @param version Version string
     * @param type Type of firmware (SETUP or UPDATE)
     * @param board Optional board type
     * @param validateChecksum Whether to validate checksums (defaults to true)
     * @return Download result with data and checksums
     */
    suspend fun downloadFirmware(
        product: String,
        version: String,
        type: FirmwareType,
        board: String? = null,
        validateChecksum: Boolean = true
    ): FirmwareDownloadResult = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/download".toHttpUrl().newBuilder()
            .addQueryParameter("product", product)
            .addQueryParameter("version", version)
            .addQueryParameter("type", type.value)

        board?.let { urlBuilder.addQueryParameter("board", it) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException("Network error: ${e.message}", e)
        }

        response.use { resp ->
            handleStatusCode(resp.code, resp.body?.string())

            val data = resp.body?.bytes() ?: throw NetworkException("Empty response body")
            val md5 = resp.header("X-Checksum-MD5") ?: resp.header("Content-MD5")
            val sha256 = resp.header("X-Checksum-SHA256")

            val result = FirmwareDownloadResult(data, md5, sha256)

            if (validateChecksum && !result.validateChecksums()) {
                throw ChecksumMismatchException()
            }

            result
        }
    }

    // MARK: - Private Helpers

    private fun performRequest(url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException("Network error: ${e.message}", e)
        }

        return response.use { resp ->
            val body = resp.body?.string()
            handleStatusCode(resp.code, body)
            body ?: throw NetworkException("Empty response body")
        }
    }

    private fun handleStatusCode(statusCode: Int, body: String?) {
        when (statusCode) {
            in 200..299 -> return

            400 -> {
                val message = parseErrorMessage(body)
                throw BadRequestException(message)
            }

            404 -> {
                val message = parseErrorMessage(body)
                throw NotFoundException(message)
            }

            429 -> {
                // Note: Retry-After header would need to be captured from response
                throw RateLimitExceededException()
            }

            else -> {
                val message = parseErrorMessage(body)
                throw ServerException(statusCode, message)
            }
        }
    }

    private fun parseErrorMessage(body: String?): String? {
        if (body.isNullOrEmpty()) return null

        return try {
            val errorResponse = json.decodeFromString<ErrorResponse>(body)
            errorResponse.message ?: errorResponse.error
        } catch (e: Exception) {
            body
        }
    }
}
