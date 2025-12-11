package com.fyrbyadditive.zapsdk

/**
 * Base exception for all ZAP SDK errors
 */
sealed class ZAPException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * The request URL could not be constructed
 */
class InvalidURLException(
    message: String = "Invalid URL"
) : ZAPException(message)

/**
 * A network error occurred
 */
class NetworkException(
    message: String,
    cause: Throwable? = null
) : ZAPException(message, cause)

/**
 * The server returned an error response
 */
class ServerException(
    val statusCode: Int,
    message: String?
) : ZAPException(message ?: "Server error: $statusCode")

/**
 * The response could not be decoded
 */
class DecodingException(
    message: String,
    cause: Throwable? = null
) : ZAPException(message, cause)

/**
 * Rate limit exceeded - retry after the specified number of seconds
 */
class RateLimitExceededException(
    val retryAfterSeconds: Int? = null
) : ZAPException(
    if (retryAfterSeconds != null) {
        "Rate limit exceeded. Retry after $retryAfterSeconds seconds."
    } else {
        "Rate limit exceeded"
    }
)

/**
 * The requested resource was not found
 */
class NotFoundException(
    message: String? = null
) : ZAPException(message ?: "Resource not found")

/**
 * Invalid request parameters
 */
class BadRequestException(
    message: String? = null
) : ZAPException(message ?: "Bad request")

/**
 * Checksum validation failed for downloaded firmware
 */
class ChecksumMismatchException(
    message: String = "Downloaded file checksum does not match expected value"
) : ZAPException(message)
