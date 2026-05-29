package com.airs.android.provisioning.data

import com.airs.android.provisioning.model.WifiNetwork

class MockWifiNetworkRepository : WifiNetworkRepository { // 단위 테스트와 Preview에서 사용할 Wi-Fi 목록 mock
    override suspend fun loadNetworks(): List<WifiNetwork> { // 실제 Android Wi-Fi API 없이 고정 후보를 반환
        return listOf(
            WifiNetwork(ssid = "AIRS-LAB", rssi = -42, isConnected = true), // 현재 연결된 Wi-Fi처럼 보여줄 mock
            WifiNetwork(ssid = "Sogang-WiFi", rssi = -63, isConnected = false), // 주변 Wi-Fi 후보 mock
            WifiNetwork(ssid = "AIRS-Guest", rssi = -71, isConnected = false) // 주변 Wi-Fi 후보 mock
        )
    }
}
