package com.airs.android.provisioning.data

import com.airs.android.provisioning.model.AirsBleNode
import com.airs.android.provisioning.model.ProvisioningStatus
import com.airs.android.provisioning.model.ProvisioningStatusType
import com.airs.android.provisioning.model.WifiCredentials
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockBleProvisioningRepository : BleProvisioningRepository { // 실제 BLE 없이 앱 흐름을 검증하는 가짜 repository
    // 주변 AIRS 노드를 검색하는 상황을 시간차로 흉내 냄
    override fun scanForNodes(): Flow<List<AirsBleNode>> = flow {
        delay(600) // 검색 중인 느낌을 주기 위해 잠시 대기
        emit(
            listOf(
                AirsBleNode(
                    id = "mock-node-0001", // mock 노드의 내부 식별자
                    displayName = "AIRS-FAKE-0001", // 태블릿 UI 테스트용 가짜 노드 표시 이름
                    rssi = -48, // 비교적 가까운 BLE 신호 세기
                    firmwareVersion = "mock-0.1.0" // mock firmware 버전
                )
            )
        ) // 첫 번째 노드가 발견된 상태를 화면에 전달
    }

    // 선택한 노드와 BLE 연결을 맺는 상황을 흉내 냄
    override suspend fun connect(node: AirsBleNode) {
        delay(700) // 실제 연결 지연처럼 보이도록 잠시 대기
    }

    // Wi-Fi 정보를 노드에 전달하고 상태 변화를 순서대로 흉내 냄
    override fun provisionWifi(
        node: AirsBleNode, // Wi-Fi 정보를 받을 mock 노드
        credentials: WifiCredentials // 사용자가 입력한 SSID/PW
    ): Flow<ProvisioningStatus> = flow {
        emit(ProvisioningStatus(ProvisioningStatusType.Received, "${node.displayName}에서 설정값을 받았습니다.")) // 노드가 payload를 수신한 상태
        delay(700) // 다음 상태로 넘어가기 전 대기
        emit(ProvisioningStatus(ProvisioningStatusType.WifiConnecting, "노드가 Wi-Fi 연결을 시도합니다.")) // Wi-Fi 연결 시도 상태
        delay(900) // Wi-Fi 연결 시도 시간을 흉내 냄

        if (credentials.ssid.contains("fail", ignoreCase = true)) {
            emit(ProvisioningStatus(ProvisioningStatusType.WifiFailed, "mock 실패: SSID에 fail이 포함되어 있습니다.")) // 실패 UI 확인용 상태
            return@flow // 실패했으므로 이후 성공 상태를 보내지 않고 flow 종료
        }

        emit(ProvisioningStatus(ProvisioningStatusType.WifiConnected, "노드가 Wi-Fi에 연결되었습니다.")) // Wi-Fi provisioning의 최종 성공 상태
    }
}
