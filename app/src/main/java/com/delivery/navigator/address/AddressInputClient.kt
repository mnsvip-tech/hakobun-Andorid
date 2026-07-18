package com.delivery.navigator.address

data class AddressCandidate(
    val source: AddressSource,
    val postalCode: String,
    val address: String,
    val confidence: Float
)

enum class AddressSource {
    CameraOcr,
    PostalCode
}

interface CameraAddressRecognitionClient {
    suspend fun recognizeAddress(imageBytes: ByteArray): List<AddressCandidate>
}

interface PostalCodeAddressClient {
    suspend fun searchByPostalCode(postalCode: String): List<AddressCandidate>
}
