# ZAP SDK for Android

A Kotlin SDK for the ZAP Firmware API, providing easy access to firmware updates and product information.

## Requirements

- Android API 28+ (Android 9.0+)
- Kotlin 1.9+
- Android Studio Hedgehog or later

## Installation

### Gradle (Kotlin DSL)

Add the SDK to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.fyrbyadditive:zapsdk:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.fyrbyadditive:zapsdk:1.0.0'
}
```

### Local Module

To include the SDK as a local module:

1. Copy the `zapsdk` folder to your project
2. Add to `settings.gradle.kts`:
   ```kotlin
   include(":zapsdk")
   ```
3. Add the dependency:
   ```kotlin
   implementation(project(":zapsdk"))
   ```

## Permissions

The SDK requires internet permission. Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Quick Start

```kotlin
import com.fyrbyadditive.zapsdk.*

// Create a client
val client = ZAPClient()

// Fetch available products (from a coroutine)
val products = client.getProducts()

// Get latest firmware for a product
val firmware = client.getLatestFirmware(product = "fame-printer")

// Download firmware
val download = client.downloadFirmware(
    product = "fame-printer",
    version = "1.0.0",
    type = FirmwareType.SETUP
)

// Access the firmware data
val firmwareBytes = download.data
```

## API Reference

### ZAPClient

The main client for interacting with the ZAP API.

#### Initialization

```kotlin
// Default configuration
val client = ZAPClient()

// Custom configuration
val client = ZAPClient(
    baseUrl = "https://custom.api.com",
    httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .build()
)
```

#### Products

```kotlin
// Get all products
val products: List<ZAPProduct> = client.getProducts()
```

#### Firmware

```kotlin
// Get latest firmware
val firmware = client.getLatestFirmware(
    product = "fame-printer",
    channel = "stable",  // Optional, defaults to "stable"
    board = null         // Optional board filter
)

// Get firmware history
val history = client.getFirmwareHistory(
    product = "fame-printer",
    channel = "stable",
    board = null
)

// Get specific version
val version = client.getFirmware(
    product = "fame-printer",
    version = "1.2.0",
    board = null
)
```

#### Downloads

```kotlin
// Download firmware binary
val result = client.downloadFirmware(
    product = "fame-printer",
    version = "1.0.0",
    type = FirmwareType.SETUP,  // SETUP or UPDATE
    board = null,
    validateChecksum = true     // Optional, defaults to true
)

// Access download data
val data: ByteArray = result.data
val md5: String? = result.md5
val sha256: String? = result.sha256

// Manual checksum validation
val isValid = result.validateChecksums()
```

### Models

#### ZAPProduct

```kotlin
data class ZAPProduct(
    val slug: String,        // Unique identifier
    val name: String,        // Display name
    val description: String, // Product description
    val imageUrl: String?    // Optional image URL
)
```

#### ZAPFirmware

```kotlin
data class ZAPFirmware(
    val version: String,                    // e.g., "1.2.0"
    val channel: String,                    // e.g., "stable"
    val board: String?,                     // Board type
    val releaseNotes: String?,              // Changelog
    val publishedAt: String?,               // Release date (ISO 8601)
    val requirements: FirmwareRequirements?,
    val downloads: FirmwareDownloads?
)
```

#### FirmwareType

```kotlin
enum class FirmwareType(val value: String) {
    SETUP("setup"),  // Full firmware for initial setup
    UPDATE("update") // Incremental update
}
```

### Error Handling

All methods can throw `ZAPException` subtypes:

```kotlin
try {
    val firmware = client.getLatestFirmware(product = "fame-printer")
} catch (e: ZAPException) {
    when (e) {
        is InvalidURLException -> {
            Log.e(TAG, "Invalid URL")
        }
        is NetworkException -> {
            Log.e(TAG, "Network error: ${e.message}")
        }
        is ServerException -> {
            Log.e(TAG, "Server error ${e.statusCode}: ${e.message}")
        }
        is DecodingException -> {
            Log.e(TAG, "Decoding error: ${e.message}")
        }
        is RateLimitExceededException -> {
            Log.e(TAG, "Rate limited. Retry after: ${e.retryAfterSeconds} seconds")
        }
        is NotFoundException -> {
            Log.e(TAG, "Not found: ${e.message}")
        }
        is BadRequestException -> {
            Log.e(TAG, "Bad request: ${e.message}")
        }
        is ChecksumMismatchException -> {
            Log.e(TAG, "Checksum mismatch")
        }
    }
}
```

## Rate Limits

The API has the following rate limits:

- **API endpoints:** 60 requests/minute per IP
- **Downloads:** 20 downloads/minute per IP

When rate limited, the SDK throws `RateLimitExceededException`.

## Example Usage

### With ViewModel

```kotlin
class FirmwareViewModel : ViewModel() {
    private val client = ZAPClient()

    private val _products = MutableStateFlow<List<ZAPProduct>>(emptyList())
    val products: StateFlow<List<ZAPProduct>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                _products.value = client.getProducts()
            } catch (e: ZAPException) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

### With Jetpack Compose

```kotlin
@Composable
fun ProductListScreen(viewModel: FirmwareViewModel = viewModel()) {
    val products by viewModel.products.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProducts()
    }

    when {
        isLoading -> CircularProgressIndicator()
        error != null -> Text("Error: $error")
        else -> LazyColumn {
            items(products) { product ->
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text(product.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
```

## ProGuard Rules

If you're using ProGuard/R8, add these rules to your `proguard-rules.pro`:

```proguard
# ZAP SDK
-keep class com.fyrbyadditive.zapsdk.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fyrbyadditive.zapsdk.**$$serializer { *; }
-keepclassmembers class com.fyrbyadditive.zapsdk.** {
    *** Companion;
}
-keepclasseswithmembers class com.fyrbyadditive.zapsdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
```

## License

Copyright (c) Fyr by Additive. All rights reserved.
