package com.airs.android.provisioning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airs.android.provisioning.data.BleProvisioningRepository
import com.airs.android.provisioning.data.MockBleProvisioningRepository
import com.airs.android.provisioning.data.MockWifiNetworkRepository
import com.airs.android.provisioning.data.WifiNetworkRepository
import com.airs.android.provisioning.domain.WifiConfigPayload
import com.airs.android.provisioning.model.AirsBleNode
import com.airs.android.provisioning.model.ProvisioningStep
import com.airs.android.provisioning.model.ProvisioningStatusType
import com.airs.android.provisioning.model.ProvisioningUiState
import com.airs.android.provisioning.model.WifiCredentials
import com.airs.android.provisioning.model.WifiNetwork
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ProvisioningViewModel(
    private val bleRepository: BleProvisioningRepository = MockBleProvisioningRepository(), // 테스트에서 바로 쓰기 위한 기본 BLE mock repository
    private val wifiNetworkRepository: WifiNetworkRepository = MockWifiNetworkRepository() // 테스트에서 바로 쓰기 위한 기본 Wi-Fi 목록 mock repository
) : ViewModel() { // 화면 상태와 사용자 이벤트를 관리하는 ViewModel
    var uiState by mutableStateOf(ProvisioningUiState()) // Compose가 관찰하는 화면 상태
        private set // 화면 밖에서 uiState를 직접 바꾸지 못하게 제한

    private var scanJob: Job? = null // 진행 중인 노드 검색 coroutine 작업
    private var provisioningJob: Job? = null // 진행 중인 Wi-Fi 전송 coroutine 작업
    private var wifiNetworkJob: Job? = null // 진행 중인 Wi-Fi 목록 로딩 coroutine 작업

    // 사용자가 노드 검색 시작 버튼을 눌렀을 때 실행
    fun startScan() {
        scanJob?.cancel() // 이미 검색 중이면 이전 검색 작업을 취소
        uiState = ProvisioningUiState(step = ProvisioningStep.Scanning) // 화면을 검색 중 상태로 초기화
        scanJob = viewModelScope.launch { // ViewModel 생명주기에 묶인 coroutine으로 scan 시작
            bleRepository.scanForNodes()
                .catch { error ->
                    uiState = uiState.copy(
                        step = ProvisioningStep.Failure, // 검색 중 예외가 나면 실패 화면으로 이동
                        errorMessage = error.message ?: "노드 검색 중 오류가 발생했습니다." // 예외 메시지가 없으면 기본 메시지 사용
                    )
                }
                .collect { nodes ->
                    uiState = uiState.copy(
                        step = if (nodes.isEmpty()) ProvisioningStep.Scanning else ProvisioningStep.NodeList, // 노드가 있으면 목록 화면으로 전환
                        nodes = nodes // repository가 보내준 최신 노드 목록 저장
                    )
                }
        }
    }

    // 사용자가 목록에서 연결할 노드를 선택했을 때 실행
    fun selectNode(node: AirsBleNode) {
        scanJob?.cancel() // 노드를 선택했으므로 검색 작업은 중단
        uiState = uiState.copy( // 기존 상태를 복사하면서 일부 값만 바꾸기
            step = ProvisioningStep.Connecting, // 화면을 연결 중 상태로 전환
            selectedNode = node, // 사용자가 선택한 노드를 상태에 저장
            errorMessage = null // 이전 오류 메시지 제거
        )
        viewModelScope.launch { // 연결 작업을 비동기로 실행
            runCatching { bleRepository.connect(node) }
                .onSuccess {
                    uiState = uiState.copy(step = ProvisioningStep.BluetoothConnected) // 연결 성공 안내 화면을 먼저 보여줌
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        step = ProvisioningStep.Failure, // 연결 실패 시 실패 화면으로 이동
                        errorMessage = error.message ?: "노드 연결 중 오류가 발생했습니다." // 연결 실패 메시지 저장
                    )
                }
        }
    }

    // SSID 입력창 값이 바뀔 때 실행
    fun updateSsid(value: String) {
        uiState = uiState.copy(
            ssid = value, // 기존 상태를 복사하고 SSID만 새 값으로 교체
            safePayloadPreview = buildSafePayloadPreview(value, uiState.password) // 현재 입력값으로 전송될 payload preview 갱신
        )
    }

    // Wi-Fi 비밀번호 입력창 값이 바뀔 때 실행
    fun updatePassword(value: String) {
        uiState = uiState.copy(
            password = value, // 기존 상태를 복사하고 password만 새 값으로 교체
            safePayloadPreview = buildSafePayloadPreview(uiState.ssid, value) // 현재 입력값으로 전송될 payload preview 갱신
        )
    }

    // 사용자가 Wi-Fi 후보 목록에서 SSID를 선택했을 때 실행
    fun selectWifiNetwork(network: WifiNetwork) {
        uiState = uiState.copy(
            ssid = network.ssid, // 선택한 Wi-Fi 이름을 전송 대상 SSID로 반영
            safePayloadPreview = buildSafePayloadPreview(network.ssid, uiState.password), // 선택한 SSID가 들어간 payload preview 갱신
            wifiNetworkMessage = if (network.isConnected) {
                "현재 Android 기기가 연결 중인 Wi-Fi를 선택했습니다. 비밀번호는 Android가 앱에 제공하지 않으므로 직접 입력해야 합니다."
            } else {
                "선택한 Wi-Fi의 비밀번호를 입력한 뒤 노드에 전송할 수 있습니다."
            } // Android가 저장된 비밀번호를 앱에 주지 않는다는 점을 명확히 안내
        )
    }

    // Android 기기에서 현재 연결/주변 Wi-Fi SSID 후보를 읽어옴
    fun loadWifiNetworks() {
        wifiNetworkJob?.cancel() // 이전 Wi-Fi 목록 로딩이 있으면 취소
        uiState = uiState.copy(
            isLoadingWifiNetworks = true, // 화면에 로딩 상태 표시
            wifiNetworkMessage = null // 이전 안내 메시지 제거
        )
        wifiNetworkJob = viewModelScope.launch { // ViewModel 생명주기에 묶어 Wi-Fi 목록 로딩
            runCatching { wifiNetworkRepository.loadNetworks() }
                .onSuccess { networks ->
                    uiState = uiState.copy(
                        wifiNetworks = networks, // 읽어온 Wi-Fi 후보 목록 저장
                        isLoadingWifiNetworks = false, // 로딩 종료
                        wifiNetworkMessage = if (networks.isEmpty()) {
                            "현재 연결된 Wi-Fi를 확인하지 못했습니다. 기기의 Wi-Fi와 위치 설정을 확인해 주세요."
                        } else {
                            "Wi-Fi를 선택하면 비밀번호 입력 창이 열립니다. Android는 저장된 비밀번호를 앱에 제공하지 않습니다."
                        } // 목록이 비었거나 비밀번호 자동 획득이 불가함을 안내
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        wifiNetworks = emptyList(), // 실패 시 목록 비우기
                        isLoadingWifiNetworks = false, // 로딩 종료
                        wifiNetworkMessage = error.message ?: "Wi-Fi 목록을 읽지 못했습니다. SSID를 직접 입력해 주세요." // 실패 안내
                    )
                }
        }
    }

    // 비밀번호 표시/숨김 버튼을 눌렀을 때 실행
    fun togglePasswordVisible() {
        uiState = uiState.copy(passwordVisible = !uiState.passwordVisible) // 현재 표시 상태를 반대로 전환
    }

    // Bluetooth 연결 성공 확인 후 Wi-Fi 설정 화면으로 이동
    fun continueToWifiInput() {
        uiState = uiState.copy(
            step = ProvisioningStep.WifiInput, // Wi-Fi 설정 화면으로 전환
            password = "", // 이전 입력값이 남지 않도록 비밀번호 초기화
            safePayloadPreview = "" // SSID 자동 선택 전까지 preview 숨김
        )
        loadWifiNetworks() // 현재 Android 기기가 연결 중인 Wi-Fi SSID를 읽어옴
    }

    // 사용자가 Wi-Fi 정보 전송 버튼을 눌렀을 때 실행
    fun sendWifiConfig() {
        val node = uiState.selectedNode ?: return // 선택된 노드가 없으면 더 진행하지 않음
        val credentials = WifiCredentials(
            ssid = uiState.ssid.trim(), // 앞뒤 공백을 제거한 SSID 사용
            password = uiState.password // 비밀번호는 사용자가 입력한 값을 그대로 사용
        )

        if (credentials.ssid.isBlank()) {
            uiState = uiState.copy(errorMessage = "SSID를 입력해야 합니다.") // SSID가 없으면 오류 메시지 표시
            return // SSID가 없으므로 전송 중단
        }
        if (credentials.password.isBlank()) {
            uiState = uiState.copy(errorMessage = "Wi-Fi 비밀번호를 입력해야 합니다.") // 비밀번호가 없으면 오류 메시지 표시
            return // 비밀번호가 없으므로 전송 중단
        }

        provisioningJob?.cancel() // 이미 진행 중인 전송 작업이 있으면 취소
        uiState = uiState.copy(
            step = ProvisioningStep.Provisioning, // 화면을 Wi-Fi 정보 전송 중 상태로 전환
            statuses = emptyList(), // 이전 진행 상태 목록 초기화
            safePayloadPreview = WifiConfigPayload.buildSafePreview(credentials), // 비밀번호를 가린 payload preview 생성
            errorMessage = null // 이전 오류 메시지 제거
        )

        provisioningJob = viewModelScope.launch { // Wi-Fi 정보 전달 흐름을 비동기로 시작
            bleRepository.provisionWifi(node, credentials)
                .catch { error ->
                    uiState = uiState.copy(
                        step = ProvisioningStep.Failure, // 전송 중 예외가 나면 실패 화면으로 이동
                        errorMessage = error.message ?: "Wi-Fi 정보 전달 중 오류가 발생했습니다." // 예외 메시지 저장
                    )
                }
                .collect { status ->
                    val nextStep = when (status.type) {
                        ProvisioningStatusType.WifiFailed -> ProvisioningStep.Failure // Wi-Fi 실패 상태면 실패 화면
                        ProvisioningStatusType.WifiConnected -> ProvisioningStep.Success // Wi-Fi 연결 성공 상태면 성공 화면
                        else -> ProvisioningStep.Provisioning // 그 외 상태는 계속 진행 중 화면
                    }
                    uiState = uiState.copy(
                        step = nextStep, // 계산된 다음 화면 단계 반영
                        statuses = uiState.statuses + status // 새 진행 상태를 기존 목록 뒤에 추가
                    )
                }
        }
    }

    // 처음부터 다시 진행할 때 실행
    fun retryFromStart() {
        scanJob?.cancel() // 진행 중인 검색 작업 취소
        provisioningJob?.cancel() // 진행 중인 Wi-Fi 전송 작업 취소
        wifiNetworkJob?.cancel() // 진행 중인 Wi-Fi 목록 로딩 작업 취소
        bleRepository.disconnect() // 실제 BLE 연결이 남아 있으면 정리
        uiState = ProvisioningUiState() // 모든 화면 상태를 초기값으로 되돌림
    }

    // 노드 선택은 유지하고 Wi-Fi 입력 화면으로 돌아갈 때 실행
    fun backToWifiInput() {
        provisioningJob?.cancel() // 진행 중인 Wi-Fi 전송 작업 취소
        uiState = uiState.copy(
            step = ProvisioningStep.WifiInput, // Wi-Fi 입력 단계로 되돌림
            statuses = emptyList(), // 이전 진행 상태 목록 제거
            safePayloadPreview = buildSafePayloadPreview(uiState.ssid, uiState.password), // 현재 입력값 기준 preview를 다시 표시
            errorMessage = null // 이전 오류 메시지 제거
        )
        loadWifiNetworks() // Wi-Fi 입력 화면으로 돌아올 때 SSID 후보를 다시 갱신
    }

    // ViewModel이 사라질 때 실행
    override fun onCleared() {
        bleRepository.disconnect() // 화면 종료 시 BLE GATT 자원을 정리
        super.onCleared() // 부모 ViewModel 정리 로직 실행
    }

    // 화면에 보여줄 전송 payload preview 생성
    private fun buildSafePayloadPreview(ssid: String, password: String): String {
        val trimmedSsid = ssid.trim() // 실제 전송과 같은 기준으로 SSID 앞뒤 공백 제거
        if (trimmedSsid.isBlank()) return "" // SSID가 없으면 preview를 보여주지 않음
        return WifiConfigPayload.buildSafePreview(
            WifiCredentials(
                ssid = trimmedSsid, // preview에서도 실제 전송할 SSID 사용
                password = password // 비밀번호는 buildSafePreview 안에서 마스킹됨
            )
        )
    }
}
