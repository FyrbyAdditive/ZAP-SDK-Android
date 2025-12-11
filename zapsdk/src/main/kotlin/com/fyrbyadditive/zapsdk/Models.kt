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

    /** Optional build number. When present, takes precedence over version for determining latest firmware. */
    @SerialName("build_number")
    val buildNumber: Int? = null,

    /** Release notes or changelog */
    @SerialName("release_notes")
    val releaseNotes: String? = null,

    /** Minimum app version required to flash this firmware */
    @SerialName("min_app_version_flash")
    val minAppVersionFlash: String? = null,

    /** Minimum app version required to run this firmware */
    @SerialName("min_app_version_run")
    val minAppVersionRun: String? = null,

    /** Maximum app version allowed to flash this firmware (null means no limit) */
    @SerialName("max_app_version_flash")
    val maxAppVersionFlash: String? = null,

    /** Maximum app version allowed to run this firmware (null means no limit) */
    @SerialName("max_app_version_run")
    val maxAppVersionRun: String? = null,

    /** Date the firmware was published (ISO 8601 format) */
    @SerialName("published_at")
    val publishedAt: String? = null,

    /** Download information for setup and update binaries */
    val downloads: FirmwareDownloads? = null
) {
    /**
     * Returns true if this firmware is newer than the given firmware.
     * Build numbers take precedence over version strings when present.
     */
    fun isNewerThan(other: ZAPFirmware): Boolean {
        // If both have build numbers, compare them
        if (buildNumber != null && other.buildNumber != null) {
            return buildNumber > other.buildNumber
        }

        // If only this has a build number, it's considered newer
        if (buildNumber != null && other.buildNumber == null) {
            return true
        }

        // If only other has a build number, this is older
        if (buildNumber == null && other.buildNumber != null) {
            return false
        }

        // Fall back to semantic version comparison
        return compareVersions(version, other.version) > 0
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }

            if (p1 != p2) {
                return p1 - p2
            }
        }

        return 0
    }
}

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

    /** Original filename */
    val filename: String? = null,

    /** File size in bytes */
    val size: Long? = null,

    /** MD5 checksum of the file */
    @SerialName("checksum_md5")
    val checksumMd5: String? = null,

    /** SHA256 checksum of the file */
    @SerialName("checksum_sha256")
    val checksumSha256: String? = null,

    /** Board type (if specific to a board) */
    @SerialName("board_type")
    val boardType: String? = null
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
            // Content-MD5 header uses base64 encoding per HTTP spec (RFC 2616)
            // X-Checksum-MD5 uses hex encoding
            val normalizedExpected = normalizeChecksum(expectedMD5)
            if (!actualMD5.equals(normalizedExpected, ignoreCase = true)) {
                return false
            }
        }

        sha256?.let { expectedSHA256 ->
            val actualSHA256 = data.sha256Hash()
            // SHA256 header typically uses hex encoding
            val normalizedExpected = normalizeChecksum(expectedSHA256)
            if (!actualSHA256.equals(normalizedExpected, ignoreCase = true)) {
                return false
            }
        }

        return true
    }

    /**
     * Normalizes a checksum to hex format.
     * Handles both hex strings and base64-encoded checksums (like Content-MD5 header).
     */
    private fun normalizeChecksum(checksum: String): String {
        // If it looks like base64 (contains = or has non-hex characters), decode it
        if (checksum.contains("=") || checksum.contains("+") || checksum.contains("/")) {
            return try {
                val decoded = java.util.Base64.getDecoder().decode(checksum)
                decoded.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                checksum // Return as-is if decoding fails
            }
        }
        // Already hex or invalid, return as-is
        return checksum
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

/** Generic API response wrapper */
@Serializable
internal data class APIResponse<T>(
    val success: Boolean,
    val data: T? = null
)

/** Product info returned in firmware response */
@Serializable
data class ProductInfo(
    val slug: String,
    val name: String
)

/** Channel info returned in firmware response */
@Serializable
data class ChannelInfo(
    val slug: String,
    val name: String
)

/** Response data for firmware endpoint */
@Serializable
internal data class FirmwareResponseData(
    val product: ProductInfo,
    val channel: ChannelInfo,
    @SerialName("available_channels")
    val availableChannels: List<String>,
    val firmware: ZAPFirmware
)

/** Response data for firmware history endpoint */
@Serializable
internal data class FirmwareHistoryResponseData(
    val product: ProductInfo,
    val channel: ChannelInfo,
    @SerialName("available_channels")
    val availableChannels: List<String>,
    val versions: List<ZAPFirmware>
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
