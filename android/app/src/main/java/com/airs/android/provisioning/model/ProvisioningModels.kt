package com.airs.android.provisioning.model

data class AirsBleNode( // BLE scan 결과로 발견한 AIRS 노드 하나를 표현
    val id: String, // 앱 내부에서 노드를 구분하기 위한 ID
    val displayName: String, // 사용자에게 보여줄 노드 이름
    val rssi: Int, // BLE 신호 세기
    val firmwareVersion: String // 노드 firmware 버전
)

data class WifiCredentials( // 노드에 전달할 Wi-Fi 접속 정보를 묶는 값 객체
    val ssid: String, // Wi-Fi 이름
    val password: String // Wi-Fi 비밀번호
)

data class WifiNetwork( // Android 기기에서 감지한 Wi-Fi 후보 하나를 표현
    val ssid: String, // Wi-Fi 이름
    val rssi: Int?, // Wi-Fi 신호 세기
    val isConnected: Boolean // 현재 Android 기기가 연결 중인 Wi-Fi인지 여부
)

enum class ProvisioningStep { // 앱의 현재 화면/작업 단계를 제한된 값으로 표현
    Start, // 아직 노드 검색을 시작하지 않은 상태
    Scanning, // 주변 AIRS 노드를 검색 중인 상태
    NodeList, // 발견된 노드 목록을 보여주는 상태
    Connecting, // 선택한 노드에 연결 중인 상태
    BluetoothConnected, // 선택한 노드와 BLE 연결에 성공한 상태
    WifiInput, // Wi-Fi SSID/PW를 입력받는 상태
    Provisioning, // Wi-Fi 정보를 노드에 전달하고 결과를 기다리는 상태
    Success, // Wi-Fi 설정 흐름이 성공한 상태
    Failure // Wi-Fi 설정 흐름이 실패한 상태
}

enum class ProvisioningStatusType(val label: String) { // 노드가 알려주는 진행 상태와 화면 표시 문구를 함께 정의
    Received("Wi-Fi 정보 수신"), // 노드가 SSID/PW payload를 받은 상태
    WifiConnecting("Wi-Fi 연결 중"), // 노드가 Wi-Fi 접속을 시도 중인 상태
    WifiConnected("Wi-Fi 연결 성공"), // 노드가 Wi-Fi 접속에 성공한 상태
    WifiFailed("Wi-Fi 연결 실패"), // 노드가 Wi-Fi 접속에 실패한 상태
    Unknown("알 수 없는 노드 상태") // firmware가 약속하지 않은 상태 문자열을 보낸 경우
}

data class ProvisioningStatus( // 화면에 쌓아 보여줄 진행 상태 한 줄
    val type: ProvisioningStatusType, // 상태 종류
    val message: String // 상태에 대한 상세 설명
)

data class ProvisioningUiState( // Compose 화면이 읽는 모든 상태를 한 객체로 모음
    val step: ProvisioningStep = ProvisioningStep.Start, // 현재 화면/작업 단계
    val nodes: List<AirsBleNode> = emptyList(), // 검색된 AIRS 노드 목록
    val selectedNode: AirsBleNode? = null, // 사용자가 선택한 노드
    val ssid: String = "", // 사용자가 입력 중인 Wi-Fi 이름
    val password: String = "", // 사용자가 입력 중인 Wi-Fi 비밀번호
    val passwordVisible: Boolean = false, // 비밀번호 원문 표시 여부
    val wifiNetworks: List<WifiNetwork> = emptyList(), // Android 기기에서 감지한 Wi-Fi 후보 목록
    val isLoadingWifiNetworks: Boolean = false, // Wi-Fi 후보 목록을 읽는 중인지 여부
    val wifiNetworkMessage: String? = null, // Wi-Fi 목록을 읽지 못했거나 비어 있을 때 보여줄 안내
    val statuses: List<ProvisioningStatus> = emptyList(), // provisioning 진행 상태 목록
    val safePayloadPreview: String = "", // 비밀번호를 가린 payload preview
    val errorMessage: String? = null // 화면에 보여줄 오류 메시지
) {
    val canSendWifiConfig: Boolean // Wi-Fi 정보 전송 버튼 활성화 여부
        get() = selectedNode != null && ssid.isNotBlank() && password.isNotBlank() // 선택된 노드, SSID, 비밀번호가 있을 때만 전송 가능

    val canReturnToWifiInput: Boolean // 결과 화면에서 Wi-Fi 재입력 버튼을 보여줄지 여부
        get() = selectedNode != null && statuses.isNotEmpty() // 노드와 통신을 시작한 뒤 받은 상태가 있을 때만 재입력 가능
}
