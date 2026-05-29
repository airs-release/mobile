package com.airs.android.provisioning.data

import com.airs.android.provisioning.model.AirsBleNode
import com.airs.android.provisioning.model.ProvisioningStatus
import com.airs.android.provisioning.model.WifiCredentials
import kotlinx.coroutines.flow.Flow

interface BleProvisioningRepository { // BLE provisioning 기능의 공통 계약
    // 주변 AIRS 노드를 검색하고 발견 목록을 stream(Flow)으로 반환하는 함수
    fun scanForNodes(): Flow<List<AirsBleNode>>

    // 선택한 AIRS 노드와 BLE 연결을 맺음
    suspend fun connect(node: AirsBleNode)

    // 연결된 노드에 Wi-Fi 정보를 전달하고 진행 상태를 stream으로 반환
    fun provisionWifi(
        node: AirsBleNode, // Wi-Fi 정보를 전달할 대상 노드
        credentials: WifiCredentials // 사용자가 입력한 SSID/PW
    ): Flow<ProvisioningStatus>

    // 현재 BLE 연결을 정리함
    fun disconnect() = Unit
}
