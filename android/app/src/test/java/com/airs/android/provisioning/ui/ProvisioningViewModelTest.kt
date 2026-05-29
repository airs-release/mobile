package com.airs.android.provisioning.ui

import com.airs.android.provisioning.model.ProvisioningStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProvisioningViewModelTest { // ViewModel이 mock repository 상태를 화면 단계로 올바르게 변환하는지 확인
    private val testDispatcher = StandardTestDispatcher() // viewModelScope가 테스트용 dispatcher에서 동작하도록 사용

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // ViewModel coroutine의 Main dispatcher를 테스트 dispatcher로 교체
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // 다른 테스트에 영향이 없도록 Main dispatcher를 원래대로 복구
    }

    @Test
    // 검색을 시작하면 mock 노드 목록 화면으로 이동하는지 확인
    fun startScan_loadsMockNodes() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        viewModel.startScan() // 노드 검색 시작
        advanceUntilIdle() // delay와 flow emit이 모두 끝날 때까지 테스트 시간 진행

        assertEquals(ProvisioningStep.NodeList, viewModel.uiState.step) // 노드가 발견되면 목록 화면이어야 함
        assertTrue(viewModel.uiState.nodes.isNotEmpty()) // mock 노드가 상태에 들어 있어야 함
    }

    @Test
    // 노드와 통신한 적이 없으면 Wi-Fi 재입력 버튼을 보여주지 않는지 확인
    fun initialState_doesNotAllowWifiRetry() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        assertFalse(viewModel.uiState.canReturnToWifiInput) // 선택된 노드와 진행 상태가 없으면 재입력 불가
    }

    @Test
    // 노드 연결에 성공하면 Bluetooth 성공 안내 단계로 이동하는지 확인
    fun selectNode_movesToBluetoothConnected() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        viewModel.startScan() // mock 노드 검색 시작
        advanceUntilIdle() // 노드 검색 완료까지 진행
        viewModel.selectNode(viewModel.uiState.nodes.first()) // 첫 번째 mock 노드 선택
        advanceUntilIdle() // mock 연결 완료까지 진행

        assertEquals(ProvisioningStep.BluetoothConnected, viewModel.uiState.step) // 연결 성공 안내 화면이어야 함
    }

    @Test
    // Bluetooth 성공 확인 후 Wi-Fi 후보 목록을 불러오고 현재 연결 SSID를 자동 선택하는지 확인
    fun continueToWifiInput_loadsConnectedWifi() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        viewModel.startScan() // mock 노드 검색 시작
        advanceUntilIdle() // 노드 검색 완료까지 진행
        viewModel.selectNode(viewModel.uiState.nodes.first()) // 첫 번째 mock 노드 선택
        advanceUntilIdle() // mock 연결 완료까지 진행
        viewModel.continueToWifiInput() // Wi-Fi 설정 화면으로 이동
        advanceUntilIdle() // Wi-Fi 후보 목록 로딩 완료까지 진행

        assertEquals(ProvisioningStep.WifiInput, viewModel.uiState.step) // 확인 후 Wi-Fi 입력 화면이어야 함
        assertTrue(viewModel.uiState.wifiNetworks.isNotEmpty()) // mock Wi-Fi 후보가 표시되어야 함
        assertEquals("AIRS-LAB", viewModel.uiState.ssid) // 현재 연결된 mock Wi-Fi가 자동 선택되어야 함
    }

    @Test
    // Wi-Fi 후보를 선택하면 SSID 입력값이 채워지는지 확인
    fun selectWifiNetwork_updatesSsid() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        viewModel.startScan() // mock 노드 검색 시작
        advanceUntilIdle() // 노드 검색 완료까지 진행
        viewModel.selectNode(viewModel.uiState.nodes.first()) // 첫 번째 mock 노드 선택
        advanceUntilIdle() // mock 연결 완료까지 진행
        viewModel.continueToWifiInput() // Wi-Fi 설정 화면으로 이동
        advanceUntilIdle() // Wi-Fi 후보 목록 로딩 완료까지 진행
        viewModel.selectWifiNetwork(viewModel.uiState.wifiNetworks.first()) // 첫 번째 Wi-Fi 후보 선택

        assertEquals("AIRS-LAB", viewModel.uiState.ssid) // 선택한 Wi-Fi 이름이 SSID 입력값에 반영되어야 함
        assertFalse(viewModel.uiState.canSendWifiConfig) // 비밀번호를 아직 입력하지 않았으므로 전송하면 안 됨
        assertTrue(viewModel.uiState.safePayloadPreview.contains("AIRS-LAB")) // 화면에 전송될 payload preview가 표시되어야 함
    }

    @Test
    // 정상 SSID를 전송하면 성공 화면으로 이동하는지 확인
    fun sendWifiConfig_withValidSsid_movesToSuccess() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        viewModel.startScan() // mock 노드 검색 시작
        advanceUntilIdle() // 노드 검색 완료까지 진행
        viewModel.selectNode(viewModel.uiState.nodes.first()) // 첫 번째 mock 노드 선택
        advanceUntilIdle() // mock 연결 완료까지 진행
        viewModel.continueToWifiInput() // Wi-Fi 설정 화면으로 이동
        advanceUntilIdle() // Wi-Fi 목록 로딩 완료까지 진행
        viewModel.updateSsid("AIRS-LAB") // 성공 시나리오 SSID 입력
        viewModel.updatePassword("password") // 테스트용 비밀번호 입력
        viewModel.sendWifiConfig() // Wi-Fi 정보 mock 전송
        advanceUntilIdle() // provisioning flow 완료까지 진행

        assertEquals(ProvisioningStep.Success, viewModel.uiState.step) // Wi-Fi 연결 성공 상태면 성공 화면이어야 함
        assertEquals(3, viewModel.uiState.statuses.size) // received, connecting, connected 총 세 상태가 있어야 함
        assertTrue(viewModel.uiState.safePayloadPreview.contains("********")) // 화면 preview에는 비밀번호가 마스킹되어야 함
    }

    @Test
    // 실패 SSID를 전송하면 실패 화면으로 이동하는지 확인
    fun sendWifiConfig_withFailSsid_movesToFailure() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        viewModel.startScan() // mock 노드 검색 시작
        advanceUntilIdle() // 노드 검색 완료까지 진행
        viewModel.selectNode(viewModel.uiState.nodes.first()) // 첫 번째 mock 노드 선택
        advanceUntilIdle() // mock 연결 완료까지 진행
        viewModel.continueToWifiInput() // Wi-Fi 설정 화면으로 이동
        advanceUntilIdle() // Wi-Fi 목록 로딩 완료까지 진행
        viewModel.updateSsid("AIRS-fail-test") // 실패 시나리오 SSID 입력
        viewModel.updatePassword("password") // 테스트용 비밀번호 입력
        viewModel.sendWifiConfig() // Wi-Fi 정보 mock 전송
        advanceUntilIdle() // provisioning flow 완료까지 진행

        assertEquals(ProvisioningStep.Failure, viewModel.uiState.step) // 실패 상태면 실패 화면이어야 함
        assertEquals(3, viewModel.uiState.statuses.size) // received, connecting, failed 총 세 상태가 있어야 함
        assertTrue(viewModel.uiState.canReturnToWifiInput) // Wi-Fi 전송 이후 실패이므로 재입력 버튼을 보여줄 수 있어야 함
    }

    @Test
    // SSID가 비어 있으면 전송하지 않고 오류 메시지를 표시하는지 확인
    fun sendWifiConfig_withoutSsid_setsErrorMessage() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        viewModel.startScan() // mock 노드 검색 시작
        advanceUntilIdle() // 노드 검색 완료까지 진행
        viewModel.selectNode(viewModel.uiState.nodes.first()) // 첫 번째 mock 노드 선택
        advanceUntilIdle() // mock 연결 완료까지 진행
        viewModel.continueToWifiInput() // Wi-Fi 설정 화면으로 이동
        advanceUntilIdle() // Wi-Fi 목록 로딩 완료까지 진행
        viewModel.updateSsid("") // 비어 있는 SSID 입력
        viewModel.sendWifiConfig() // Wi-Fi 정보 전송 시도

        assertEquals(ProvisioningStep.WifiInput, viewModel.uiState.step) // SSID가 없으면 입력 화면에 머물러야 함
        assertEquals("SSID를 입력해야 합니다.", viewModel.uiState.errorMessage) // 사용자에게 입력 오류를 알려야 함
    }

    @Test
    // 비밀번호가 비어 있으면 전송하지 않고 오류 메시지를 표시하는지 확인
    fun sendWifiConfig_withoutPassword_setsErrorMessage() = runTest(testDispatcher) {
        val viewModel = ProvisioningViewModel() // 기본 mock repository를 사용하는 ViewModel 생성

        viewModel.startScan() // mock 노드 검색 시작
        advanceUntilIdle() // 노드 검색 완료까지 진행
        viewModel.selectNode(viewModel.uiState.nodes.first()) // 첫 번째 mock 노드 선택
        advanceUntilIdle() // mock 연결 완료까지 진행
        viewModel.continueToWifiInput() // Wi-Fi 설정 화면으로 이동
        advanceUntilIdle() // Wi-Fi 목록 로딩 완료까지 진행
        viewModel.updateSsid("AIRS-LAB") // SSID만 입력
        viewModel.sendWifiConfig() // Wi-Fi 정보 전송 시도

        assertEquals(ProvisioningStep.WifiInput, viewModel.uiState.step) // 비밀번호가 없으면 입력 화면에 머물러야 함
        assertEquals("Wi-Fi 비밀번호를 입력해야 합니다.", viewModel.uiState.errorMessage) // 사용자에게 입력 오류를 알려야 함
    }
}
