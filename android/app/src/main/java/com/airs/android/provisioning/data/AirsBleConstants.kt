package com.airs.android.provisioning.data

import java.util.UUID

internal object AirsBleConstants { // AIRS 앱과 노드 firmware가 함께 맞춰야 하는 BLE 계약값
    const val NODE_NAME_PREFIX = "AIRS-SETUP-" // AIRS 설정 모드 노드 이름 prefix
    const val SCAN_TIMEOUT_MILLIS = 12_000L // 실제 BLE scan을 한 번 시도할 최대 시간
    const val ADD_FAKE_NODE_WHEN_SCAN_EMPTY = true // 노드 확보 전 태블릿 UI 검증용 fallback. 실제 노드 테스트 전 제거 또는 false
    const val FAKE_NODE_ID = "AIRS_FAKE_NODE_FOR_TABLET_TEST" // 실제 BLE address와 구분되는 가짜 노드 ID
    const val FAKE_NODE_NAME = "AIRS-FAKE-0001" // 노드가 없을 때 UI 테스트용으로 표시할 가짜 노드 이름

    val PROVISIONING_SERVICE_UUID: UUID = UUID.fromString("2b340f85-c8c9-4f85-ac71-4b9702e2bcf9") // AIRS provisioning service UUID
    val DEVICE_INFO_CHARACTERISTIC_UUID: UUID = UUID.fromString("88568c03-f498-4c72-9b89-afbaf87578c0") // 노드 정보 read characteristic UUID
    val WIFI_CONFIG_CHARACTERISTIC_UUID: UUID = UUID.fromString("60ed54cf-eda0-42f6-b7cd-28b88ef8b518") // Wi-Fi JSON write characteristic UUID
    val PROVISION_STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("b77420e0-566e-4746-acf0-23d88d634ce7") // Wi-Fi 연결 상태 notify characteristic UUID
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // notify 활성화에 쓰는 표준 CCCD UUID
}
