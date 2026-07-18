package com.delivery.navigator.map

import android.net.Uri
import com.delivery.navigator.address.PostalCodeAddressClient

enum class MapProviderType {
    GoogleMaps,
    Zenrin
}

data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

data class DeliveryPin(
    val id: String,
    val point: MapPoint,
    val statusLabel: String
)

interface AddressSearchClient {
    suspend fun searchAddress(query: String): List<MapPoint>
}

interface RouteNavigator {
    fun navigationUri(address: String): Uri
}

interface DeliveryMapProvider {
    val type: MapProviderType
    val addressSearchClient: AddressSearchClient
    val postalCodeAddressClient: PostalCodeAddressClient
    val routeNavigator: RouteNavigator
}
