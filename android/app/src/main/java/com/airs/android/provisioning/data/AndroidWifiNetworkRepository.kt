package com.airs.android.provisioning.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import com.airs.android.provisioning.model.WifiNetwork

class AndroidWifiNetworkRepository(
    context: Context // Android Wi-Fi API 접근에 필요한 application context
) : WifiNetworkRepository { // 실제 Android 기기의 현재/주변 Wi-Fi SSID 목록을 읽는 repository
    private val appContext = context.applicationContext // Activity 누수를 피하기 위해 application context만 보관
    private val wifiManager = appContext.getSystemService(WifiManager::class.java) // Android Wi-Fi 관리 API

    @SuppressLint("MissingPermission")
    override suspend fun loadNetworks(): List<WifiNetwork> {
        val currentSsid = wifiManager.connectionInfo?.ssid?.normalizeSsid() // 현재 Android 기기가 연결 중인 SSID
        runCatching { wifiManager.startScan() } // 최신 scan을 요청하되 실패해도 기존 scan cache를 사용할 수 있게 함

        val scannedNetworks = wifiManager.scanResults // Android가 가지고 있는 최근 Wi-Fi scan 결과
            .mapNotNull { result ->
                val ssid = result.SSID.normalizeSsid() // scan result의 SSID를 화면용 문자열로 정리
                if (ssid.isBlank()) return@mapNotNull null // 숨김 SSID나 빈 값은 목록에서 제외
                WifiNetwork(
                    ssid = ssid, // 사용자가 선택할 Wi-Fi 이름
                    rssi = result.level, // 신호 세기
                    isConnected = ssid == currentSsid // 현재 연결된 Wi-Fi이면 표시
                )
            }
            .groupBy { it.ssid } // 같은 SSID가 여러 AP로 잡히면 하나로 묶음
            .map { (_, networks) -> networks.maxBy { it.rssi ?: Int.MIN_VALUE } } // 가장 강한 신호 하나만 사용

        val withCurrentNetwork = if (currentSsid.isNullOrBlank() || scannedNetworks.any { it.ssid == currentSsid }) {
            scannedNetworks // 현재 연결 SSID가 scan 결과에 이미 있거나 알 수 없으면 그대로 사용
        } else {
            listOf(WifiNetwork(ssid = currentSsid, rssi = null, isConnected = true)) + scannedNetworks // scan cache에 없으면 현재 SSID를 맨 앞에 추가
        }

        return withCurrentNetwork.sortedWith(
            compareByDescending<WifiNetwork> { it.isConnected }
                .thenByDescending { it.rssi ?: Int.MIN_VALUE }
                .thenBy { it.ssid }
        ) // 현재 연결 Wi-Fi, 강한 신호, 이름 순으로 정렬
    }

    private fun String?.normalizeSsid(): String {
        val raw = this?.trim().orEmpty() // null을 빈 문자열로 처리하고 앞뒤 공백 제거
        if (raw == WifiManager.UNKNOWN_SSID) return "" // Android가 SSID를 숨겼거나 권한이 없을 때의 값 제거
        return raw.removeSurrounding("\"") // Android가 SSID에 붙이는 따옴표 제거
    }
}
