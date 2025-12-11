package com.fyrbyadditive.zapsdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a product available in the ZAP firmware system
 */
@Serializable
data class ZAPProduct(
    /** Unique identifier for the product */
    val slug: String,

    /** Display name of the product */
    val name: String,

    /** Product description */
    val description: String,

    /** URL to the product image */
    @SerialName("image_url")
    val imageUrl: String? = null
)

/**
 * Represents a firmware version with its metadata and download information
 */
@Serializable
data class ZAPFirmware(
    /** Firmware version string (e.g., "1.2.0") */
    val version: String,

    /** Release channel (e.g., "stable", "beta") */
    val channel: String,

    /** Board type this firmware targets */
    val board: String? = null,

    /** Release notes or changelog */
    @SerialName("release_notes")
    val releaseNotes: String? = null,

    /** Date the firmware was published (ISO 8601 format) */
    @SerialName("published_at")
    val publishedAt: String? = null,

    /** Minimum version requirements by platform */
    val requirements: FirmwareRequirements? = null,

    /** Download information for setup and update binaries */
    val downloads: FirmwareDownloads? = null
)

/**
 * Minimum version requirements for different platforms
 */
@Serializable
data class FirmwareRequirements(
    val ios: String? = null,
    val android: String? = null,
    val firmware: String? = null
)

/**
 * Download URLs and metadata for firmware binaries
 */
@Serializable
data class FirmwareDownloads(
    val setup: FirmwareDownloadInfo? = null,
    val update: FirmwareDownloadInfo? = null
)

/**
 * Information about a downloadable firmware file
 */
@Serializable
data class FirmwareDownloadInfo(
    /** Download URL for the firmware binary */
    val url: String,

    /** File size in bytes */
    val size: Long? = null,

    /** MD5 checksum of the file */
    val md5: String? = null,

    /** SHA256 checksum of the file */
    val sha256: String? = null
)

/**
 * Type of firmware binary to download
 */
enum class FirmwareType(val value: String) {
    /** Full firmware for initial setup */
    SETUP("setup"),

    /** Incremental update firmware */
    UPDATE("update")
}

/**
 * Result of a firmware download including the binary data and checksums
 */
data class FirmwareDownloadResult(
    /** The raw firmware binary data */
    val data: ByteArray,

    /** MD5 checksum from the server (if provided) */
    val md5: String?,

    /** SHA256 checksum from the server (if provided) */
    val sha256: String?
) {
    /**
     * Validates the downloaded data against the provided checksums
     * @return true if checksums match or no checksums were provided
     */
    fun validateChecksums(): Boolean {
        // If no checksums provided, consider valid
        if (md5 == null && sha256 == null) return true

        md5?.let { expectedMD5 ->
            val actualMD5 = data.md5Hash()
            if (!actualMD5.equals(expectedMD5, ignoreCase = true)) {
                return false
            }
        }

        sha256?.let { expectedSHA256 ->
            val actualSHA256 = data.sha256Hash()
            if (!actualSHA256.equals(expectedSHA256, ignoreCase = true)) {
                return false
            }
        }

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FirmwareDownloadResult

        if (!data.contentEquals(other.data)) return false
        if (md5 != other.md5) return false
        if (sha256 != other.sha256) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + (md5?.hashCode() ?: 0)
        result = 31 * result + (sha256?.hashCode() ?: 0)
        return result
    }
}

// Internal response wrappers
@Serializable
internal data class FirmwareResponse(
    val firmware: ZAPFirmware? = null,
    val versions: List<ZAPFirmware>? = null
)

@Serializable
internal data class ErrorResponse(
    val error: String? = null,
    val message: String? = null
)

// Hash extensions
internal fun ByteArray.md5Hash(): String {
    val digest = java.security.MessageDigest.getInstance("MD5")
    val hashBytes = digest.digest(this)
    return hashBytes.joinToString("") { "%02x".format(it) }
}

internal fun ByteArray.sha256Hash(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(this)
    return hashBytes.joinToString("") { "%02x".format(it) }
}
