package com.airs.android.provisioning.data

import com.airs.android.provisioning.model.WifiNetwork

interface WifiNetworkRepository { // Android 기기의 Wi-Fi 후보 목록을 가져오는 경계
    suspend fun loadNetworks(): List<WifiNetwork> // 현재 연결/주변에서 감지한 Wi-Fi 목록을 반환
}
