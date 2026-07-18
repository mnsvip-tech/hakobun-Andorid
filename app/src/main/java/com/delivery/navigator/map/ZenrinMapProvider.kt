package com.delivery.navigator.map

import android.net.Uri
import com.delivery.navigator.address.AddressCandidate
import com.delivery.navigator.address.PostalCodeAddressClient

class ZenrinRouteNavigator : RouteNavigator {
    override fun navigationUri(address: String): Uri {
        return Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    }
}

class ZenrinAddressSearchClient : AddressSearchClient {
    override suspend fun searchAddress(query: String): List<MapPoint> {
        return emptyList()
    }
}

class ZenrinPostalCodeAddressClient : PostalCodeAddressClient {
    override suspend fun searchByPostalCode(postalCode: String): List<AddressCandidate> {
        return emptyList()
    }
}

class ZenrinDeliveryMapProvider : DeliveryMapProvider {
    override val type: MapProviderType = MapProviderType.Zenrin
    override val addressSearchClient: AddressSearchClient = ZenrinAddressSearchClient()
    override val postalCodeAddressClient: PostalCodeAddressClient = ZenrinPostalCodeAddressClient()
    override val routeNavigator: RouteNavigator = ZenrinRouteNavigator()
}
