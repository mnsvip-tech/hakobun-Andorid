package com.delivery.navigator.map

import android.net.Uri
import com.delivery.navigator.address.AddressCandidate
import com.delivery.navigator.address.PostalCodeAddressClient

class GoogleMapsRouteNavigator : RouteNavigator {
    override fun navigationUri(address: String): Uri {
        return Uri.parse("google.navigation:q=${Uri.encode(address)}")
    }
}

class GoogleMapsAddressSearchClient : AddressSearchClient {
    override suspend fun searchAddress(query: String): List<MapPoint> {
        return emptyList()
    }
}

class GoogleMapsPostalCodeAddressClient : PostalCodeAddressClient {
    override suspend fun searchByPostalCode(postalCode: String): List<AddressCandidate> {
        return emptyList()
    }
}

class GoogleMapsDeliveryMapProvider : DeliveryMapProvider {
    override val type: MapProviderType = MapProviderType.GoogleMaps
    override val addressSearchClient: AddressSearchClient = GoogleMapsAddressSearchClient()
    override val postalCodeAddressClient: PostalCodeAddressClient = GoogleMapsPostalCodeAddressClient()
    override val routeNavigator: RouteNavigator = GoogleMapsRouteNavigator()
}
